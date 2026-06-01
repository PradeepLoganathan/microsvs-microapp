#!/usr/bin/env node
'use strict';
/*
 * MBSB Demo Console — tiny dependency-free helper.
 * Serves the console UI and does everything server-side (runs the local
 * scripts, pings services, reads/writes the micro-frontend manifest), so the
 * browser talks only to this origin — no CORS, and it can start/stop processes.
 *
 *   node scripts/demo-console/server.js     (or: ./scripts/demo-console.sh)
 */
const http = require('http');
const { spawn, execFile } = require('child_process');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.env.CONSOLE_PORT || '9700', 10);
const ROOT = path.resolve(__dirname, '..', '..'); // repo root
const PLATFORM = 'http://localhost:8079';
const CHANNEL = 'demo';
const SHELL_PORT = 4200;

const SERVICES = [
  { name: 'statement-service', port: 8082 },
  { name: 'product-service', port: 8085 },
  { name: 'platform-service', port: 8079 },
  { name: 'analysis-service', port: 8083 },
  { name: 'recommendation-service', port: 8084 },
  { name: 'advisor-service', port: 8086 },
  { name: 'onboarding-service', port: 8087 },
  { name: 'customer-service', port: 8088 },
  { name: 'goals-service', port: 8089 },
];
// Canonical manifest entries used when re-adding a previously-removed micro-app.
// Keep these in sync with platform-service's ManifestEndpoint.seedChannel().
const DEFAULT_ENTRIES = {
  'home':               { name: 'home',               version: '1.0.0', remoteEntry: '/bundles/home/1.0.0/main.js',               exposedModule: './Module', elementTag: 'mf-home' },
  'statement-details':  { name: 'statement-details',  version: '1.0.0', remoteEntry: '/bundles/statement-details/1.0.0/main.js',  exposedModule: './Module', elementTag: 'mf-statement-details' },
  'statement-analysis': { name: 'statement-analysis', version: '1.0.0', remoteEntry: '/bundles/statement-analysis/1.0.0/main.js', exposedModule: './Module', elementTag: 'mf-statement-analysis' },
  'recommendations':    { name: 'recommendations',    version: '1.0.0', remoteEntry: '/bundles/recommendations/1.0.0/main.js',    exposedModule: './Module', elementTag: 'mf-recommendations' },
  'advisor':            { name: 'advisor',            version: '1.0.0', remoteEntry: '/bundles/advisor/1.0.0/main.js',            exposedModule: './Module', elementTag: 'mf-advisor' },
  'onboarding':         { name: 'onboarding',         version: '1.0.0', remoteEntry: '/bundles/onboarding/1.0.0/main.js',         exposedModule: './Module', elementTag: 'mf-onboarding' },
  'prelogin':           { name: 'prelogin',           version: '1.0.0', remoteEntry: '/bundles/prelogin/1.0.0/main.js',           exposedModule: './Module', elementTag: 'mf-prelogin' },
};

const SEED_TARGETS = [
  ['statement-service', 'http://localhost:8082/accounts/seed'],
  ['product-service', 'http://localhost:8085/products/seed'],
  ['platform-service', 'http://localhost:8079/microfrontends/seed'],
];

// --- tiny promise HTTP client (services are plain http://localhost) ---
function request(method, url, body) {
  return new Promise((resolve) => {
    const u = new URL(url);
    const data = body ? Buffer.from(JSON.stringify(body)) : null;
    const req = http.request(
      {
        method,
        hostname: u.hostname,
        port: u.port,
        path: u.pathname + u.search,
        headers: data ? { 'Content-Type': 'application/json', 'Content-Length': data.length } : {},
        timeout: 4000,
      },
      (res) => {
        let b = '';
        res.on('data', (c) => (b += c));
        res.on('end', () => resolve({ ok: true, status: res.statusCode, body: b }));
      }
    );
    req.on('error', () => resolve({ ok: false, status: 0, body: '' }));
    req.on('timeout', () => { req.destroy(); resolve({ ok: false, status: 0, body: '' }); });
    if (data) req.write(data);
    req.end();
  });
}

async function getStatus(personaParam) {
  const services = [];
  for (const s of SERVICES) {
    const r = await request('GET', `http://localhost:${s.port}/`); // any response = up
    services.push({ name: s.name, port: s.port, up: r.ok });
  }
  const shell = await request('GET', `http://localhost:${SHELL_PORT}/`);
  services.push({ name: 'banking-shell (UI)', port: SHELL_PORT, up: shell.ok });

  // Look up the statement-analysis version + the list of present micro-apps on
  // the channel the console currently tracks, so the v1/v2 segment and the
  // tabs toggle both reflect that persona's manifest.
  const channel = channelFor(personaParam);
  let version = null;
  let microapps = [];
  const m = await request('GET', `${PLATFORM}/microfrontends/manifest/${channel}`);
  if (m.ok && m.status === 200) {
    try {
      const entries = JSON.parse(m.body).microfrontends || [];
      microapps = entries.map((x) => x.name);
      const sa = entries.find((x) => x.name === 'statement-analysis');
      if (sa) version = sa.version;
    } catch (_) {}
  }
  return { services, version, channel, microapps };
}

function channelFor(persona) {
  if (!persona || persona === 'default') return CHANNEL;
  return `${CHANNEL}-${persona}`;
}

async function setVersion(version, persona) {
  const channel = channelFor(persona);
  const m = await request('GET', `${PLATFORM}/microfrontends/manifest/${channel}`);
  if (!m.ok || m.status !== 200) return { ok: false, error: `platform-service unreachable or manifest not seeded for ${channel}` };
  let manifest;
  try { manifest = JSON.parse(m.body); } catch (_) { return { ok: false, error: 'manifest is not valid JSON' }; }
  const sa = (manifest.microfrontends || []).find((x) => x.name === 'statement-analysis');
  if (!sa) return { ok: false, error: 'statement-analysis not in manifest' };
  sa.version = version;
  sa.remoteEntry = `/bundles/statement-analysis/${version}/main.js`;
  const put = await request('PUT', `${PLATFORM}/microfrontends/manifest/${channel}`, manifest);
  return { ok: put.ok && put.status >= 200 && put.status < 300, status: put.status, version, channel };
}

// Adds/removes a single micro-app entry on the active persona's manifest.
// "Adding" uses the canonical default entry from DEFAULT_ENTRIES so a previously
// removed app comes back with the same routing the platform-service would seed.
async function setTabPresence(name, present, persona) {
  if (!DEFAULT_ENTRIES[name]) return { ok: false, error: `unknown micro-app: ${name}` };
  const channel = channelFor(persona);
  const m = await request('GET', `${PLATFORM}/microfrontends/manifest/${channel}`);
  if (!m.ok || m.status !== 200) return { ok: false, error: `manifest not seeded for ${channel}` };
  let manifest;
  try { manifest = JSON.parse(m.body); } catch (_) { return { ok: false, error: 'bad manifest' }; }

  const isPresent = (manifest.microfrontends || []).some((x) => x.name === name);
  if (present === isPresent) return { ok: true, status: 'noop', channel, name, action: 'kept' };

  if (present) {
    manifest.microfrontends.push(DEFAULT_ENTRIES[name]);
  } else {
    manifest.microfrontends = manifest.microfrontends.filter((x) => x.name !== name);
  }

  const put = await request('PUT', `${PLATFORM}/microfrontends/manifest/${channel}`, manifest);
  return { ok: put.ok && put.status >= 200 && put.status < 300, status: put.status, channel, name, action: present ? 'added' : 'removed' };
}

async function seed() {
  const out = {};
  for (const [name, url] of SEED_TARGETS) {
    const r = await request('POST', url);
    out[name] = r.ok ? r.status : 'unreachable';
  }
  return out;
}

function startServices() {
  try { fs.mkdirSync(path.join(ROOT, 'logs'), { recursive: true }); } catch (_) {}
  const out = fs.openSync(path.join(ROOT, 'logs', 'demo-console-start.log'), 'a');
  const child = spawn('bash', [path.join(ROOT, 'scripts', 'run-local.sh')], {
    cwd: ROOT, detached: true, stdio: ['ignore', out, out],
  });
  child.unref();
  return { started: true, pid: child.pid, log: 'logs/demo-console-start.log' };
}

function startShell() {
  const dir = path.join(ROOT, 'mobile', 'banking-shell');
  try { fs.mkdirSync(path.join(ROOT, 'logs'), { recursive: true }); } catch (_) {}
  const out = fs.openSync(path.join(ROOT, 'logs', 'demo-console-ui.log'), 'a');
  const hasModules = fs.existsSync(path.join(dir, 'node_modules'));
  const cmd = hasModules ? 'npm start' : 'npm install && npm start'; // first run installs deps
  const child = spawn('bash', ['-c', cmd], { cwd: dir, detached: true, stdio: ['ignore', out, out] });
  child.unref();
  return { started: true, pid: child.pid, installedDeps: !hasModules, log: 'logs/demo-console-ui.log' };
}

function stopServices() {
  return new Promise((resolve) => {
    execFile('bash', [path.join(ROOT, 'scripts', 'stop-local.sh')], { cwd: ROOT, timeout: 30000 },
      (err, stdout, stderr) => resolve({ ok: !err, out: String(stdout || '') + String(stderr || '') }));
  });
}

// --- server ---
function send(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(obj));
}
function readBody(req) {
  return new Promise((resolve) => { let b = ''; req.on('data', (c) => (b += c)); req.on('end', () => resolve(b)); });
}

const server = http.createServer(async (req, res) => {
  const u = new URL(req.url, `http://localhost:${PORT}`);
  try {
    if (req.method === 'GET' && (u.pathname === '/' || u.pathname === '/index.html')) {
      const data = fs.readFileSync(path.join(__dirname, 'index.html'));
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(data);
    }
    if (u.pathname === '/api/status' && req.method === 'GET') {
      return send(res, 200, await getStatus(u.searchParams.get('persona')));
    }
    if (u.pathname === '/api/seed' && req.method === 'POST') return send(res, 200, await seed());
    if (u.pathname === '/api/start' && req.method === 'POST') return send(res, 202, { backend: startServices(), ui: startShell() });
    if (u.pathname === '/api/stop' && req.method === 'POST') return send(res, 200, await stopServices());
    if (u.pathname === '/api/version' && req.method === 'POST') {
      let v = null, persona = null;
      try { const b = JSON.parse(await readBody(req)); v = b.version; persona = b.persona; } catch (_) {}
      if (v !== '1.0.0' && v !== '2.0.0') return send(res, 400, { ok: false, error: 'version must be "1.0.0" or "2.0.0"' });
      return send(res, 200, await setVersion(v, persona));
    }
    if (u.pathname === '/api/tab' && req.method === 'POST') {
      let name = null, present = null, persona = null;
      try { const b = JSON.parse(await readBody(req)); name = b.name; present = b.present; persona = b.persona; } catch (_) {}
      if (typeof name !== 'string' || typeof present !== 'boolean') {
        return send(res, 400, { ok: false, error: 'body must be {name: string, present: boolean, persona?: string}' });
      }
      return send(res, 200, await setTabPresence(name, present, persona));
    }
    send(res, 404, { error: 'not found' });
  } catch (e) {
    send(res, 500, { error: String(e) });
  }
});

server.listen(PORT, () => {
  console.log(`\n  MBSB Demo Console  ->  http://localhost:${PORT}`);
  console.log('  (Ctrl-C stops the console; your services keep running)\n');
});
