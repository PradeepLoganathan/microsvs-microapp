/*
 * WhatsApp demo panel (vanilla JS, demo chrome — not part of the banking app).
 * Opt-in with ?whatsapp=1. Renders a mock WhatsApp chat beside the phone that
 * talks to the REAL wealth-advisor agent and can raise a home-financing lead
 * which then surfaces in the app (cross-channel).
 *
 * Override defaults with ?advisor=<base>&customer=<id>.
 */
(function () {
  var params = new URLSearchParams(window.location.search);
  if (params.get('whatsapp') !== '1') return; // disabled by default

  var ADVISOR_BASE = params.get('advisor') || 'http://localhost:8086';
  var CUSTOMER = params.get('customer') || 'acc-1001';

  var panel = document.getElementById('waPanel');
  if (!panel) return;
  document.body.classList.add('wa-on');
  panel.setAttribute('aria-hidden', 'false');

  // ---- DOM helpers ----
  function el(tag, cls, text) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function now() {
    var d = new Date();
    return ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);
  }

  // ---- scaffold ----
  var phone = el('div', 'wa-phone');
  var header = el('div', 'wa-header');
  var avatar = el('div', 'wa-avatar', 'M');
  var peer = el('div', 'wa-peer');
  var nameRow = el('div', 'wa-name');
  nameRow.textContent = 'MBSB Bank ';
  nameRow.appendChild(el('span', 'wa-verified', '✓'));
  peer.appendChild(nameRow);
  peer.appendChild(el('div', 'wa-status', 'online'));
  header.appendChild(avatar);
  header.appendChild(peer);

  var body = el('div', 'wa-body');
  body.id = 'waBody';

  var inputRow = el('div', 'wa-input');
  var input = el('input');
  input.type = 'text';
  input.placeholder = 'Message';
  var send = el('button', 'wa-send', '➤');
  inputRow.appendChild(input);
  inputRow.appendChild(send);

  phone.appendChild(header);
  phone.appendChild(body);
  phone.appendChild(inputRow);
  panel.innerHTML = '';
  panel.appendChild(phone);

  // ---- chat primitives ----
  function scroll() { body.scrollTop = body.scrollHeight; }

  function addMsg(side, text) {
    var m = el('div', 'wa-msg ' + (side === 'out' ? 'wa-out' : 'wa-in'));
    m.appendChild(document.createTextNode(text));
    m.appendChild(el('span', 'wa-time', now()));
    body.appendChild(m);
    scroll();
  }

  var typingEl = null;
  function typing(on) {
    if (on && !typingEl) {
      typingEl = el('div', 'wa-typing', 'MBSB Bank is typing…');
      body.appendChild(typingEl);
      scroll();
    } else if (!on && typingEl) {
      body.removeChild(typingEl);
      typingEl = null;
    }
  }

  function clearChips() {
    var chips = body.querySelector('.wa-quick');
    if (chips) body.removeChild(chips);
  }

  function addChips(labels) {
    clearChips();
    var wrap = el('div', 'wa-quick');
    labels.forEach(function (label) {
      var chip = el('button', 'wa-chip', label);
      chip.onclick = function () { clearChips(); ask(label, label + TOPIC); };
      wrap.appendChild(chip);
    });
    body.appendChild(wrap);
    scroll();
  }

  function addApplyButton() {
    var btn = el('button', 'wa-apply', 'Apply for Home Financing');
    btn.onclick = function () {
      btn.disabled = true;
      btn.textContent = 'Submitting…';
      fetch(ADVISOR_BASE + '/advisor/' + encodeURIComponent(CUSTOMER) + '/home-financing/apply', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount: 0, note: 'Requested via WhatsApp' }),
      }).then(function (r) {
        if (!r.ok) throw new Error('apply failed');
        addMsg('in', '✓ Done! Your home-financing application is in — it now shows in your MBSB app, and a banker will call you shortly.');
      }).catch(function () {
        btn.disabled = false;
        btn.textContent = 'Apply for Home Financing';
        addMsg('in', '⚠️ Could not submit just now. Please try again.');
      });
    };
    body.appendChild(btn);
    scroll();
  }

  // The agent only sees the user's text, not the bank's WhatsApp nudge, so we
  // attach the channel/topic as context on every turn to keep it on-topic.
  var TOPIC = ' (Context for the advisor: the customer is replying on WhatsApp to'
    + " MBSB's proactive MBSB Home Financing-i (home loan) offer. Keep the conversation"
    + ' focused on home financing — use their monthly surplus and the home_financing_i'
    + ' product to give indicative numbers — unless they clearly change the subject.)';

  // ---- talk to the real advisor agent ----
  // displayText is shown in the chat bubble; sendText is what the agent receives.
  function ask(displayText, sendText) {
    addMsg('out', displayText);
    input.value = '';
    typing(true);
    fetch(ADVISOR_BASE + '/advisor/' + encodeURIComponent(CUSTOMER) + '/ask', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: sendText || displayText }),
    }).then(function (r) {
      if (!r.ok) throw new Error('ask failed');
      return r.json();
    }).then(function (resp) {
      typing(false);
      addMsg('in', resp.message || '…');
      var a = resp.action;
      if ((a && a.type === 'ADVISOR_HUMAN') || resp.needsHuman) {
        addApplyButton();
      }
    }).catch(function () {
      typing(false);
      addMsg('in', '⚠️ I could not reach the bank just now. (Is advisor-service running, with OPENAI_API_KEY set?)');
    });
  }

  send.onclick = function () {
    var v = input.value.trim();
    if (v) ask(v, v + TOPIC);
  };
  input.addEventListener('keydown', function (e) {
    var v = input.value.trim();
    if (e.key === 'Enter' && v) ask(v, v + TOPIC);
  });

  // ---- proactive, data-driven nudge ----
  setTimeout(function () {
    addMsg('in', 'Hi Sara 👋 This is MBSB Bank.\nWe noticed steady salary credits and regular rent payments — you may qualify for MBSB Home Financing-i. Want to explore your options?');
    addChips(['How much can I borrow?', 'What are the rates?', 'Tell me more', 'Not now']);
  }, 500);
})();
