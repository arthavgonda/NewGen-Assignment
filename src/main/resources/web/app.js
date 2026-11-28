(function () {
  'use strict';

  const view = document.getElementById('view');
  const userInput = document.getElementById('user');
  const tip = document.getElementById('tip');
  let meta = null;

  const LABELS = {
    DRAFT: 'Draft', UNDER_REVIEW: 'Under review', CREDIT_APPROVAL: 'Credit approval', APPROVED: 'Approved',
    REJECTED: 'Rejected', DISBURSED: 'Disbursed', CORE_BANKING_PUSH: 'Core banking push', EXIT: 'Closed'
  };
  const ACTIONS = {
    CREATED: 'Draft opened', UPDATED: 'Draft edited', SUBMITTED: 'Submitted for assessment',
    AUTO_REJECTED: 'Auto-rejected', REVIEW_APPROVED: 'Passed review', REVIEW_REJECTED: 'Rejected at review',
    CREDIT_APPROVED: 'Loan sanctioned', CREDIT_REJECTED: 'Declined at credit', FORM_GENERATED: 'Application form generated',
    DISBURSED: 'Amount disbursed', CORE_BANKING_PUSH: 'Pushed to core banking', EXITED: 'File closed'
  };
  const ACTION_ICON = {
    CREATED: 'file-plus', UPDATED: 'pencil', SUBMITTED: 'send', AUTO_REJECTED: 'circle-x', REVIEW_APPROVED: 'clipboard-check',
    REVIEW_REJECTED: 'circle-x', CREDIT_APPROVED: 'shield-check', CREDIT_REJECTED: 'circle-x', FORM_GENERATED: 'scroll-text',
    DISBURSED: 'banknote', CORE_BANKING_PUSH: 'landmark', EXITED: 'circle-check'
  };
  const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  // ---------- basics ----------

  function icon(name, cls) {
    const body = (window.ICONS || {})[name] || '';
    return `<svg class="i${cls ? ' ' + cls : ''}" viewBox="0 0 24 24" aria-hidden="true">${body}</svg>`;
  }

  function paintIcons(root) {
    root.querySelectorAll('[data-icon]').forEach(el => {
      if (!el.querySelector('svg')) el.insertAdjacentHTML('afterbegin', icon(el.dataset.icon));
    });
  }

  function esc(v) {
    if (v === null || v === undefined) return '';
    return String(v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  async function api(method, path, body) {
    const opts = { method, headers: { 'X-User-Code': userInput.value || 'WEBUSER' } };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch('/api/' + path, opts);
    const data = await res.json().catch(() => ({ error: 'Unexpected response from server' }));
    if (!res.ok) {
      const err = new Error(data.error || 'Request failed');
      err.details = data.details || [];
      throw err;
    }
    return data;
  }

  function fmt(n, dp = 2) {
    if (n === null || n === undefined || n === '' || isNaN(Number(n))) return '';
    const neg = Number(n) < 0;
    const [whole, frac] = Math.abs(Number(n)).toFixed(dp).split('.');
    const last3 = whole.slice(-3);
    const rest = whole.slice(0, -3);
    return (neg ? '-' : '') + (rest ? rest.replace(/\B(?=(\d{2})+(?!\d))/g, ',') + ',' : '') + last3 + (dp ? '.' + frac : '');
  }

  const rupee = t => (t === '' || t === null || t === undefined) ? '–' : '₹' + t;
  const short = n => {
    const v = Number(n);
    if (v >= 1e7) return '₹' + (v / 1e7).toFixed(v % 1e7 ? 1 : 0) + 'Cr';
    if (v >= 1e5) return '₹' + (v / 1e5).toFixed(v % 1e5 ? 1 : 0) + 'L';
    if (v >= 1e3) return '₹' + Math.round(v / 1e3) + 'k';
    return '₹' + Math.round(v);
  };
  const num = s => Number(String(s === null || s === undefined ? '0' : s).replace(/,/g, ''));

  function parseWhen(text) {
    const m = /^(\d{2})-(\d{2})-(\d{4})(?: (\d{2}):(\d{2}))?/.exec(text || '');
    return m ? new Date(+m[3], +m[2] - 1, +m[1], +(m[4] || 0), +(m[5] || 0)) : null;
  }

  function when(text) {
    const d = parseWhen(text);
    if (!d) return '';
    return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}, ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }

  function day(text) {
    const d = parseWhen(text);
    return d ? `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}` : '';
  }

  function ago(text) {
    const d = parseWhen(text);
    if (!d || !meta) return '';
    const now = new Date(meta.businessDate + 'T' + new Date().toTimeString().slice(0, 8));
    const mins = Math.round((now - d) / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return mins + ' min' + (mins === 1 ? '' : 's') + ' ago';
    const h = Math.round(mins / 60);
    if (h < 24) return h + ' hour' + (h === 1 ? '' : 's') + ' ago';
    const dd = Math.round(h / 24);
    return dd + ' day' + (dd === 1 ? '' : 's') + ' ago';
  }

  function initials(name) {
    return String(name || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0].toUpperCase()).join('');
  }

  function avatar(name, id) {
    return `<span class="avatar c${(Number(id) % 4) + 1}">${esc(initials(name))}</span>`;
  }

  function pill(status) {
    return `<span class="pill ${esc(status)}">${esc(LABELS[status] || status)}</span>`;
  }

  function alertBox(kind, msg, details) {
    const ic = kind === 'ok' ? 'circle-check' : (kind === 'err' ? 'triangle-alert' : 'bell');
    let html = `<div class="alert ${kind}">${icon(ic)}<div>${esc(msg)}`;
    if (details && details.length) html += '<ul>' + details.map(d => `<li>${esc(d)}</li>`).join('') + '</ul>';
    return html + '</div></div>';
  }

  function showError(target, err) {
    target.innerHTML = alertBox('err', err.message, err.details);
    target.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }

  function formData(form) {
    const out = {};
    new FormData(form).forEach((v, k) => { out[k] = typeof v === 'string' ? v.trim() : v; });
    return out;
  }

  function pageHead(title, sub, actions) {
    return `<div class="page-head"><div><h1>${title}</h1>${sub ? `<div class="sub">${sub}</div>` : ''}</div>
      ${actions ? `<div class="actions">${actions}</div>` : ''}</div>`;
  }

  function cardHead(title, right) {
    return `<div class="card-head"><h3 class="card-title">${esc(title)}</h3>${right || ''}</div>`;
  }

  function table(columns, rows, opts) {
    opts = opts || {};
    if (!rows.length) return `<div class="empty">${icon(opts.emptyIcon || 'inbox')}${esc(opts.empty || 'Nothing here yet.')}</div>`;
    const th = columns.map(c => `<th class="${c.r ? 'r' : ''}">${esc(c.label)}</th>`).join('');
    const body = rows.map(r => {
      const link = opts.link ? ` class="go" data-href="${esc(opts.link(r))}"` : '';
      return `<tr${link}>` + columns.map(c => {
        const raw = typeof c.value === 'function' ? c.value(r) : r[c.key];
        return `<td class="${c.r ? 'r ' : ''}${c.cls || ''}">${c.html ? raw : esc(raw)}</td>`;
      }).join('') + '</tr>';
    }).join('');
    return `<div class="tbl-wrap"><table class="tbl"><thead><tr>${th}</tr></thead><tbody>${body}</tbody></table></div>`;
  }

  function bindLinks(root) {
    root.querySelectorAll('tr.go').forEach(tr => tr.addEventListener('click', e => {
      if (e.target.closest('a, button')) return;
      location.hash = tr.dataset.href;
    }));
  }

  function showTip(e, html) {
    tip.innerHTML = html;
    tip.hidden = false;
    const r = tip.getBoundingClientRect();
    let x = e.clientX + 14;
    let y = e.clientY - r.height - 10;
    if (x + r.width > window.innerWidth - 8) x = e.clientX - r.width - 14;
    if (y < 8) y = e.clientY + 16;
    tip.style.left = x + 'px';
    tip.style.top = y + 'px';
  }
  const hideTip = () => { tip.hidden = true; };

  // ---------- charts ----------

  function niceMax(v) {
    if (v <= 0) return 1;
    const p = Math.pow(10, Math.floor(Math.log10(v)));
    const n = v / p;
    return [1, 1.2, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10].find(step => n <= step) * p;
  }

  function lineChart(points, keys) {
    const W = 720, H = 250, L = 52, R = 10, T = 14, B = 30;
    const max = niceMax(Math.max(1, ...points.flatMap(p => keys.map(k => p[k]))));
    const x = i => L + (i * (W - L - R)) / Math.max(1, points.length - 1);
    const y = v => T + (H - T - B) * (1 - v / max);
    let svg = `<svg viewBox="0 0 ${W} ${H}" role="img">`;
    for (let g = 0; g <= 4; g++) {
      const v = (max / 4) * g;
      svg += `<line class="grid-line" x1="${L}" x2="${W - R}" y1="${y(v)}" y2="${y(v)}"/>`;
      svg += `<text class="axis" x="${L - 10}" y="${y(v) + 4}" text-anchor="end">${g === 0 ? '0' : short(v)}</text>`;
    }
    points.forEach((p, i) => {
      const every = Math.max(1, Math.ceil(points.length / 7));
      if ((points.length - 1 - i) % every === 0) {
        const d = new Date(p.date + 'T00:00:00');
        svg += `<text class="axis" x="${x(i)}" y="${H - 8}" text-anchor="middle">${d.getDate()} ${MONTHS[d.getMonth()]}</text>`;
      }
    });
    const path = k => points.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(p[k]).toFixed(1)}`).join('');
    svg += `<path class="s1-area" d="${path(keys[0])}L${x(points.length - 1)},${y(0)}L${x(0)},${y(0)}Z"/>`;
    svg += `<path class="s2-line" d="${path(keys[1])}"/><path class="s1-line" d="${path(keys[0])}"/>`;
    points.forEach((p, i) => {
      if (p[keys[1]] > 0) svg += `<circle class="s2-dot" cx="${x(i)}" cy="${y(p[keys[1]])}" r="4"/>`;
      if (p[keys[0]] > 0) svg += `<circle class="s1-dot" cx="${x(i)}" cy="${y(p[keys[0]])}" r="4.5"/>`;
    });
    const w = (W - L - R) / Math.max(1, points.length - 1);
    points.forEach((p, i) => {
      svg += `<rect class="hit" data-i="${i}" x="${x(i) - w / 2}" y="${T}" width="${w}" height="${H - T - B}"/>`;
    });
    return svg + '</svg>';
  }

  function gauge(value, limit, segments = 30) {
    const has = value !== null && value !== undefined && value !== '';
    const v = has ? Math.max(0, Math.min(100, Number(value))) : 0;
    const start = 200, sweep = 220;
    const cx = 120, cy = 112, r1 = 78, r2 = 104;
    let svg = '<svg viewBox="0 0 240 150">';
    for (let s = 0; s < segments; s++) {
      const pct = (s + 0.5) / segments * 100;
      const a = (start - sweep * (s + 0.5) / segments) * Math.PI / 180;
      const x1 = cx + r1 * Math.cos(a), y1 = cy - r1 * Math.sin(a);
      const x2 = cx + r2 * Math.cos(a), y2 = cy - r2 * Math.sin(a);
      let color = 'var(--gauge-off)';
      let op = 1;
      if (has && pct <= v) {
        color = pct > limit ? 'var(--red)' : 'var(--gauge-on)';
        op = 0.55 + 0.45 * (s / segments);
      }
      svg += `<line x1="${x1.toFixed(1)}" y1="${y1.toFixed(1)}" x2="${x2.toFixed(1)}" y2="${y2.toFixed(1)}" stroke="${color}" stroke-opacity="${op.toFixed(2)}" stroke-width="7.5" stroke-linecap="round"/>`;
    }
    const la = (start - sweep * limit / 100) * Math.PI / 180;
    svg += `<line x1="${(cx + (r1 - 10) * Math.cos(la)).toFixed(1)}" y1="${(cy - (r1 - 10) * Math.sin(la)).toFixed(1)}" x2="${(cx + (r2 + 6) * Math.cos(la)).toFixed(1)}" y2="${(cy - (r2 + 6) * Math.sin(la)).toFixed(1)}" stroke="var(--text)" stroke-width="2"/>`;
    return svg + '</svg>';
  }

  function schedule(principal, rate, months) {
    const r = rate / 1200;
    const emi = r === 0 ? principal / months : principal * r * Math.pow(1 + r, months) / (Math.pow(1 + r, months) - 1);
    const rows = [];
    let bal = principal;
    for (let m = 1; m <= months; m++) {
      const interest = bal * r;
      const princ = emi - interest;
      bal = Math.max(0, bal - princ);
      rows.push({ m, emi, interest, princ, bal });
    }
    return rows;
  }

  // first: Date in the month of the first EMI, today: business date
  function repaymentBars(rows, first, today) {
    // long loans are grouped by quarter or half year so the chart stays readable
    const size = rows.length <= 24 ? 1 : (rows.length <= 60 ? 3 : 6);
    const startKey = first.getFullYear() * 12 + first.getMonth();
    const nowKey = today.getFullYear() * 12 + today.getMonth();
    const monthOf = k => ({ m: k % 12, y: Math.floor(k / 12) });
    const name = k => `${MONTHS[monthOf(k).m]} ${monthOf(k).y}`;
    const groups = [];
    for (let i = 0; i < rows.length; i += size) {
      const chunk = rows.slice(i, i + size);
      const from = startKey + i;
      const to = from + chunk.length - 1;
      const { m, y } = monthOf(from);
      groups.push({
        from, to,
        label: MONTHS[m],
        year: i === 0 || y !== monthOf(from - size).y ? String(y) : '',
        title: size === 1 ? `${name(from)} · EMI ${i + 1} of ${rows.length}` : `${name(from)} – ${name(to)} · EMIs ${i + 1}–${i + chunk.length}`,
        pay: chunk.reduce((s, x) => s + x.emi, 0), princ: chunk.reduce((s, x) => s + x.princ, 0),
        interest: chunk.reduce((s, x) => s + x.interest, 0), bal: chunk[chunk.length - 1].bal
      });
    }
    let current = groups.findIndex(g => nowKey >= g.from && nowKey <= g.to);
    let kind = 'now';
    if (current < 0) {
      kind = nowKey < startKey ? 'first' : 'done';
      current = kind === 'first' ? 0 : groups.length - 1;
    }
    const top = niceMax(Math.max(...groups.map(g => g.pay)));
    const cols = groups.map((g, i) => `<div class="col${i === current ? ' on' : ''}" data-i="${i}">
      <div class="track"><div class="fill" style="height:${(g.pay / top * 100).toFixed(1)}%;opacity:.35"></div>
      <div class="fill" style="height:${(g.princ / top * 100).toFixed(1)}%"></div></div><span>${g.label}</span><em>${g.year}</em></div>`).join('');
    return {
      html: `<div class="bars dated"><div class="yaxis"><span>${short(top)}</span><span>${short(top * 2 / 3)}</span><span>${short(top / 3)}</span><span>0</span></div>${cols}</div>`,
      groups, current, kind, firstName: name(startKey)
    };
  }

  // ---------- chrome ----------

  function syncUser() {
    const code = userInput.value || 'MAKER01';
    document.getElementById('me-code').textContent = code;
    document.getElementById('me-avatar').textContent = code[0];
    document.getElementById('side-avatar').textContent = code[0];
  }

  try {
    const saved = localStorage.getItem('los.user');
    if (saved) userInput.value = saved;
  } catch (e) { /* storage blocked */ }
  syncUser();
  userInput.addEventListener('change', () => {
    userInput.value = userInput.value.trim().toUpperCase() || 'MAKER01';
    try { localStorage.setItem('los.user', userInput.value); } catch (e) { /* ignore */ }
    syncUser();
  });

  const themeBtn = document.getElementById('theme');
  function themeIcon() {
    const dark = document.documentElement.getAttribute('data-theme') === 'dark';
    themeBtn.innerHTML = icon(dark ? 'sun' : 'moon');
    themeBtn.title = dark ? 'Light mode' : 'Dark mode';
  }
  themeBtn.addEventListener('click', () => {
    const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    try { localStorage.setItem('los.theme', next); } catch (e) { /* ignore */ }
    themeIcon();
  });

  const bell = document.getElementById('bell');
  const menu = document.getElementById('alerts-menu');
  bell.addEventListener('click', async e => {
    e.stopPropagation();
    if (!menu.hidden) { menu.hidden = true; return; }
    const alerts = await api('GET', 'utilities/alerts').catch(() => []);
    menu.innerHTML = '<h4>Overdue reviews</h4>' + (alerts.length ? alerts.slice(0, 6).map(a =>
      `<a href="#/applications/${a.applicationId}">${esc(a.message)}<small>${esc(ago(a.createdAt))}</small></a>`).join('')
      : '<div class="empty" style="padding:14px">No alerts</div>') + '<a href="#/utilities" style="color:var(--brand-strong);font-weight:600">Open utilities</a>';
    menu.hidden = false;
  });
  document.addEventListener('click', e => { if (!menu.contains(e.target)) menu.hidden = true; });

  async function refreshMeta() {
    meta = await api('GET', 'meta');
    const d = new Date(meta.businessDate + 'T00:00:00');
    document.getElementById('bdate').textContent = `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
    document.getElementById('side-foot').innerHTML = `Business date<b>${d.toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' })}</b>${meta.usingSystemDate ? '' : 'set manually'}`;
  }

  async function refreshCounts(days) {
    try {
      const d = await api('GET', 'dashboard' + (days ? '?days=' + days : ''));
      document.querySelectorAll('[data-count]').forEach(el => {
        const n = d.counts[el.dataset.count];
        el.textContent = n ? n : '';
      });
      document.getElementById('alert-count').textContent = d.alerts ? d.alerts : '';
      return d;
    } catch (e) {
      return null;
    }
  }

  const appCols = [
    { label: 'Application', html: true, value: a => `<span class="strong">${esc(a.applicationNumber)}</span><span class="sub">${esc(day(a.createdAt))}</span>` },
    { label: 'Customer', html: true, value: a => `<div class="who">${avatar(a.customerName, a.customerId)}<div>${esc(a.customerName)}<span class="sub">${esc(a.branchName)}</span></div></div>` },
    { label: 'Product', value: a => a.product === 'PERSONAL' ? 'Personal' : 'Education' },
    { label: 'Amount', html: true, value: a => `<span class="amount">₹${esc(a.requestedAmountText)}</span>` },
    { label: 'Tenure', value: a => a.tenureMonths + ' mo' },
    { label: 'EMI', r: true, html: true, value: a => a.proposedEmiText ? '₹' + esc(a.proposedEmiText) : '<span class="faint">–</span>' },
    { label: 'Status', html: true, value: a => pill(a.status) },
    { label: '', r: true, html: true, value: a => `<a class="btn view" href="#/applications/${a.applicationId}">View</a>` }
  ];

  // ---------- dashboard ----------

  let trendDays = 30;

  async function dashboard() {
    const days = trendDays;
    const [d, recent] = await Promise.all([refreshCounts(days), api('GET', 'applications')]);
    if (!d) throw new Error('Could not load the dashboard');
    const c = d.counts;
    const total = Object.values(c).reduce((s, n) => s + n, 0);
    const active = (c.DRAFT || 0) + (c.UNDER_REVIEW || 0) + (c.CREDIT_APPROVAL || 0) + (c.APPROVED || 0);
    const closed = (c.EXIT || 0) + (c.REJECTED || 0) + (c.DISBURSED || 0);
    const good = (c.APPROVED || 0) + (c.DISBURSED || 0) + (c.EXIT || 0);
    const rate = good + (c.REJECTED || 0) ? Math.round(good * 100 / (good + (c.REJECTED || 0))) : 0;
    const trend = d.trend.map(t => ({ date: t.date, requested: Number(t.requested), disbursed: Number(t.disbursed), apps: t.applications }));
    const t = d.totals;
    const requests = recent.filter(a => a.status === 'DRAFT' || a.status === 'UNDER_REVIEW').slice(0, 5);
    const overdue = d.overdue.rows.map(r => ({ number: r[0], customer: r[1], amount: r[4], days: Number(r[6]) }));

    view.innerHTML = `
      ${pageHead('Dashboard', `${total} applications on file · business date ${esc(meta.businessDateText)}`,
        `<a class="btn" href="#/customers/new">${icon('user-plus')}New customer</a><a class="btn primary" href="#/applications/new">${icon('plus')}New application</a>`)}
      <div class="grid g4 mb">
        <div class="card stat c-green"><div class="label">Applications</div><div class="value">${total}</div>
          <div class="meta"><span>Active <b>${active}</b></span><span>Closed <b>${closed}</b></span></div></div>
        <div class="card stat c-orange"><div class="label">Amount disbursed</div><div class="value">${rupee(t.disbursed.replace(/\.00$/, ''))}</div>
          <div class="meta"><span>Sanctioned <b>${short(num(t.sanctioned))}</b></span><span>Pending <b>${short(num(t.pending))}</b></span></div></div>
        <div class="card stat c-pink"><div class="label">In review</div><div class="value">${(c.UNDER_REVIEW || 0) + (c.CREDIT_APPROVAL || 0)}</div>
          <div class="meta"><span>Overdue <b>${overdue.length}</b></span><span>At credit <b>${c.CREDIT_APPROVAL || 0}</b></span></div></div>
        <div class="card stat c-blue"><div class="label">Approval rate</div><div class="value">${rate}%</div>
          <div class="meta"><span>Approved <b>${good}</b></span><span>Rejected <b>${c.REJECTED || 0}</b></span></div></div>
      </div>
      <div class="grid g-wide mb">
        <div class="card">
          ${cardHead('Requested vs disbursed', `<div class="legend"><span class="s1"><i></i>Requested</span><span class="s2"><i></i>Disbursed</span><select class="chip-select" id="range" aria-label="Period">${[7, 30, 90].map(n => `<option value="${n}" ${n === days ? 'selected' : ''}>Last ${n} days</option>`).join('')}</select></div>`)}
          <div class="card-body chart" id="trend">${lineChart(trend, ['requested', 'disbursed'])}</div>
        </div>
        <div class="card" style="display:flex;flex-direction:column">
          ${cardHead('Portfolio summary')}
          <div class="card-body" style="flex:1"><ul class="kv dots">
            <li>Total requested<b>${rupee(t.requested)}</b></li>
            <li>Waiting for a decision<b>${rupee(t.in_review)}</b></li>
            <li>Total sanctioned<b>${rupee(t.sanctioned)}</b></li>
            <li>Total disbursed<b>${rupee(t.disbursed)}</b></li>
            <li>Disbursed in ${esc(d.monthName)}<b>${rupee(d.monthDisbursedText)}</b></li>
          </ul></div>
          <div class="card-foot">Awaiting disbursement <b>${rupee(t.pending)}</b></div>
        </div>
      </div>
      <div class="grid g2">
        <div class="card">${cardHead('Overdue reviews', `<a class="btn sm ghost" href="#/queue/UNDER_REVIEW">Review queue</a>`)}
          ${table([
            { label: 'Application', html: true, value: o => `<span class="strong">${esc(o.number)}</span>` },
            { label: 'Customer', key: 'customer' },
            { label: 'Amount', html: true, value: o => '₹' + esc(o.amount) },
            { label: 'Waiting', html: true, value: o => `<span class="pill late">${o.days} days</span>` }
          ], overdue, { link: o => '#/applications/' + o.number, empty: 'No review is older than two days.', emptyIcon: 'circle-check' })}</div>
        <div class="card">${cardHead('New loan requests', `<a class="btn sm ghost" href="#/applications">All applications</a>`)}
          ${table([
            { label: 'Customer', html: true, value: a => `<div class="who">${avatar(a.customerName, a.customerId)}<div>${esc(a.customerName)}<span class="sub">${a.product === 'PERSONAL' ? 'Personal' : 'Education'} loan</span></div></div>` },
            { label: 'Requested', html: true, value: a => '₹' + esc(a.requestedAmountText) },
            { label: 'Status', html: true, value: a => pill(a.status) },
            { label: 'Action', html: true, value: a => `<a class="btn view" href="#/applications/${a.applicationId}">View</a>` }
          ], requests, { link: a => '#/applications/' + a.applicationId, empty: 'No new requests.' })}</div>
      </div>`;
    bindLinks(view);
    view.querySelector('#range').addEventListener('change', e => {
      trendDays = Number(e.target.value);
      dashboard();
    });
    view.querySelectorAll('#trend .hit').forEach(h => {
      h.addEventListener('mousemove', e => {
        const p = trend[Number(h.dataset.i)];
        const dt = new Date(p.date + 'T00:00:00');
        showTip(e, `<b>${dt.getDate()} ${MONTHS[dt.getMonth()]} ${dt.getFullYear()}</b><div><span>Requested</span><span>₹${fmt(p.requested, 0)}</span></div>
          <div><span>Disbursed</span><span>₹${fmt(p.disbursed, 0)}</span></div><div><span>New files</span><span>${p.apps}</span></div>`);
      });
      h.addEventListener('mouseleave', hideTip);
    });
  }

  // ---------- customers ----------

  async function customers(params) {
    const by = params.get('by') || '';
    const q = params.get('q') || '';
    const list = await api('GET', `customers?by=${encodeURIComponent(by)}&q=${encodeURIComponent(q)}`);
    view.innerHTML = `
      ${pageHead('Customers', q ? `${list.length} result${list.length === 1 ? '' : 's'} for “${esc(q)}”` : 'Recently registered customers',
        `<a class="btn primary" href="#/customers/new">${icon('user-plus')}New customer</a>`)}
      <div class="card">
        <form class="filters" id="search">
          <div class="field"><label>Search by</label><select name="by">
            <option value="">Anything</option><option value="ID">Customer ID</option><option value="NAME">Name</option><option value="MOBILE">Mobile</option></select></div>
          <div class="field" style="flex:1;max-width:380px"><label>Value</label><input name="q" value="${esc(q)}" placeholder="e.g. 9810012345 or Sharma"></div>
          <button class="btn primary">${icon('search')}Search</button>
          ${q ? '<a class="btn ghost" href="#/customers">Clear</a>' : ''}
        </form>
        ${table([
          { label: 'Customer', html: true, value: c => `<div class="who">${avatar(c.name, c.customerId)}<div><span class="strong">${esc(c.name)}</span><span class="sub">CUST-${String(c.customerId).padStart(5, '0')}</span></div></div>` },
          { label: 'Mobile', key: 'mobile' },
          { label: 'Date of birth', key: 'dateOfBirthText' },
          { label: 'Age', value: c => c.age === null ? '' : c.age + ' yrs' },
          { label: 'KYC', html: true, value: c => c.kycType ? `<span class="pill ok">${esc(c.kycType)}</span>` : '<span class="pill wait">None</span>' },
          { label: 'Registered', value: c => day(c.createdAt) },
          { label: '', r: true, html: true, value: c => `<a class="btn view" href="#/customers/${c.customerId}">View</a>` }
        ], list, { link: c => '#/customers/' + c.customerId, empty: 'No customer matches that search.', emptyIcon: 'users' })}
      </div>`;
    view.querySelector('select[name=by]').value = by;
    view.querySelector('#search').addEventListener('submit', e => {
      e.preventDefault();
      const f = formData(e.target);
      location.hash = `#/customers?by=${encodeURIComponent(f.by)}&q=${encodeURIComponent(f.q)}`;
    });
    bindLinks(view);
  }

  function customerForm(c) {
    c = c || {};
    const kyc = meta.kycTypes.map(k => `<option ${c.kycType === k ? 'selected' : ''}>${k}</option>`).join('');
    return `<div class="form">
      <div class="field wide"><label>Full name</label><input name="name" required value="${esc(c.name)}"></div>
      <div class="field"><label>Date of birth</label><input type="date" name="dateOfBirth" required value="${esc(c.dateOfBirth)}"></div>
      <div class="field"><label>Mobile</label><input name="mobile" required maxlength="10" inputmode="numeric" value="${esc(c.mobile)}"><div class="hint">10 digits, unique per customer</div></div>
      <div class="field wide"><label>Address</label><input name="address" value="${esc(c.address)}"></div>
      <div class="field"><label>Email (optional)</label><input type="email" name="email" value="${esc(c.email)}"></div>
      <div class="field"><label>KYC document</label><select name="kycType"><option value="">None</option>${kyc}</select></div>
      <div class="field"><label>KYC number</label><input name="kycNumber" value="${esc(c.kycNumber)}"></div>
    </div>`;
  }

  async function newCustomer() {
    view.innerHTML = `
      ${pageHead('New customer', 'Address and KYC go into the core banking record after disbursement')}
      <div id="msg"></div>
      <div class="grid g-wide">
        <form class="card" id="f">${cardHead('Customer details')}<div class="card-body">${customerForm()}
          <div class="form-actions"><button class="btn primary">${icon('check')}Save customer</button><a class="btn ghost" href="#/customers">Cancel</a></div></div></form>
        <div class="card" style="align-self:start">${cardHead('Checked on save')}<div class="card-body">
          <ul class="checks">
            <li>${icon('check')}Date of birth is not in the future</li>
            <li>${icon('check')}Mobile has exactly 10 digits</li>
            <li>${icon('check')}Mobile is not registered already</li>
            <li>${icon('check')}PAN looks like ABCDE1234F, Aadhaar has 12 digits</li>
          </ul>
          <p class="muted" style="font-size:13px;margin:14px 0 0">Age (21 to 60) is checked later, when an application is submitted.</p></div></div>
      </div>`;
    view.querySelector('#f').addEventListener('submit', async e => {
      e.preventDefault();
      try {
        const c = await api('POST', 'customers', formData(e.target));
        location.hash = '#/customers/' + c.customerId + '?created=1';
      } catch (err) { showError(view.querySelector('#msg'), err); }
    });
  }

  async function customerDetail(id, params) {
    const c = await api('GET', 'customers/' + id);
    view.innerHTML = `
      ${pageHead('Customer profile', `Registered ${esc(day(c.createdAt))}`,
        `<a class="btn primary" href="#/applications/new?customer=${c.customerId}">${icon('file-plus')}New application</a>`)}
      <div id="msg">${params.get('created') ? alertBox('ok', 'Customer saved.') : ''}</div>
      <div class="grid" style="grid-template-columns:320px minmax(0,1fr);align-items:start">
        <div class="card">
          <div class="profile"><span class="avatar lg c${(c.customerId % 4) + 1}">${esc(initials(c.name))}</span>
            <div class="name">${esc(c.name)}</div><div class="id">CUST-${String(c.customerId).padStart(5, '0')}${c.age === null ? '' : ' · ' + c.age + ' years'}</div></div>
          <ul class="info-rows">
            <li>${icon('calendar')}<span>Born</span><b>${esc(c.dateOfBirthText)}</b></li>
            <li>${icon('user')}<span>Mobile</span><b>${esc(c.mobile)}</b></li>
            <li>${icon('send')}<span>Email</span><b>${esc(c.email || '–')}</b></li>
            <li>${icon('shield-check')}<span>KYC</span><b>${c.kycType ? esc(c.kycType + ' ' + (c.kycNumber || '')) : '–'}</b></li>
            <li>${icon('building-2')}<span>Address</span><b>${esc(c.address || '–')}</b></li>
          </ul>
          <div style="padding:0 20px 20px"><button class="btn" id="edit" style="width:100%">${icon('pencil')}Edit details</button></div>
        </div>
        <div>
          <div class="card">${cardHead('Applications', `<span class="muted">${c.applications.length} on file</span>`)}
            ${table(appCols.filter(col => col.label !== 'Customer'), c.applications, { link: a => '#/applications/' + a.applicationId, empty: 'No applications yet.' })}</div>
          <form class="card" id="f" hidden>${cardHead('Edit customer')}<div class="card-body">${customerForm(c)}
            <div class="form-actions"><button class="btn primary">Save changes</button><button type="button" class="btn ghost" id="cancel">Cancel</button></div></div></form>
        </div>
      </div>`;
    bindLinks(view);
    const form = view.querySelector('#f');
    view.querySelector('#edit').addEventListener('click', () => { form.hidden = false; form.scrollIntoView({ behavior: 'smooth' }); });
    view.querySelector('#cancel').addEventListener('click', () => { form.hidden = true; });
    form.addEventListener('submit', async e => {
      e.preventDefault();
      try {
        await api('PUT', 'customers/' + id, formData(form));
        await customerDetail(id, new URLSearchParams());
        view.querySelector('#msg').innerHTML = alertBox('ok', 'Details updated.');
      } catch (err) { showError(view.querySelector('#msg'), err); }
    });
  }

  // ---------- applications ----------

  async function applications(params) {
    const status = params.get('status') || '';
    const product = params.get('product') || '';
    const [list, d] = await Promise.all([api('GET', `applications?status=${status}&product=${product}`), refreshCounts()]);
    const tabs = [['', 'All'], ...['DRAFT', 'UNDER_REVIEW', 'CREDIT_APPROVAL', 'APPROVED', 'DISBURSED', 'EXIT', 'REJECTED'].map(s => [s, LABELS[s]])];
    const counts = d ? d.counts : {};
    const all = Object.values(counts).reduce((s, n) => s + n, 0);
    view.innerHTML = `
      ${pageHead('Applications', `${list.length} shown`, `<a class="btn primary" href="#/applications/new">${icon('plus')}New application</a>`)}
      <div class="card">
        <div class="tabs">${tabs.map(([s, l]) => `<a href="#/applications?status=${s}&product=${product}" class="${s === status ? 'active' : ''}">${l}<span class="n">${s ? (counts[s] || 0) : all}</span></a>`).join('')}</div>
        <form class="filters" id="filter" style="border-bottom:0;padding-bottom:6px">
          <div class="field"><label>Product</label><select name="product"><option value="">Both products</option>
            <option value="PERSONAL" ${product === 'PERSONAL' ? 'selected' : ''}>Personal</option>
            <option value="EDUCATION" ${product === 'EDUCATION' ? 'selected' : ''}>Education</option></select></div>
        </form>
        ${table(appCols, list, { link: a => '#/applications/' + a.applicationId, empty: 'No applications match these filters.' })}
      </div>`;
    view.querySelector('#filter').addEventListener('change', e => {
      location.hash = `#/applications?status=${status}&product=${formData(e.currentTarget).product}`;
    });
    bindLinks(view);
  }

  async function queue(status) {
    const list = (await api('GET', 'applications?status=' + status)).sort((x, y) => x.applicationId - y.applicationId);
    const info = {
      UNDER_REVIEW: ['Review queue', 'Eligible applications waiting for a reviewer, oldest first', 'Nothing to review, the queue is clear.'],
      CREDIT_APPROVAL: ['Credit approval', 'Reviewed and recommended, waiting for sanction', 'Nothing waiting for credit approval.'],
      APPROVED: ['Disbursement', 'Sanctioned loans, each paid out once for the full amount', 'Nothing to disburse right now.']
    }[status];
    const cols = appCols.filter(c => c.label !== 'Status' && c.label !== '');
    if (status === 'UNDER_REVIEW') {
      cols.push({ label: 'Waiting', html: true, value: a => `<span class="pill ${(a.daysWaiting || 0) > 2 ? 'late' : 'wait'}">${a.daysWaiting || 0} day${a.daysWaiting === 1 ? '' : 's'}</span>` });
    }
    cols.push({ label: '', r: true, html: true, value: a => `<a class="btn view" href="#/applications/${a.applicationId}">Open</a>` });
    view.innerHTML = `
      ${pageHead(info[0], info[1])}
      <div class="card">${table(cols, list, { link: a => '#/applications/' + a.applicationId, empty: info[2], emptyIcon: 'circle-check' })}</div>`;
    bindLinks(view);
    refreshCounts();
  }

  function applicationForm(a) {
    a = a || {};
    const branches = meta.branches.map(b => `<option value="${esc(b.code)}" ${a.branchCode === b.code ? 'selected' : ''}>${esc(b.name)} (${esc(b.code)})</option>`).join('');
    return `<div class="form">
      <div class="field"><label>Product</label><select name="product">
        <option value="PERSONAL" ${a.product === 'PERSONAL' ? 'selected' : ''}>Personal loan</option>
        <option value="EDUCATION" ${a.product === 'EDUCATION' ? 'selected' : ''}>Education loan</option></select>
        <div class="hint" data-rulehint></div></div>
      <div class="field"><label>Branch</label><select name="branchCode">${branches}</select></div>
      <div class="field"><label>Amount requested (₹)</label><input name="requestedAmount" required inputmode="decimal" value="${esc(a.requestedAmount)}"></div>
      <div class="field"><label>Tenure (months)</label><input name="tenureMonths" required inputmode="numeric" value="${esc(a.tenureMonths)}"></div>
      <div class="field"><label>Monthly net income (₹)</label><input name="monthlyIncome" required inputmode="decimal" value="${esc(a.monthlyIncome)}"></div>
      <div class="field"><label>Existing EMIs per month (₹)</label><input name="existingObligations" inputmode="decimal" value="${esc(a.existingObligations || '0')}"></div>
      <div class="field wide"><label>Co-borrower (optional)</label><input name="coBorrowerName" value="${esc(a.coBorrowerName)}"></div>
    </div>`;
  }

  async function newApplication(params) {
    const preset = params.get('customer');
    let customer = null;
    view.innerHTML = `
      <div class="case">
        <div class="case-main">
          <div class="case-title"><a class="icon-sq" href="#/applications">${icon('arrow-left')}</a><h1>New loan application</h1></div>
          <div id="msg" style="margin-top:18px"></div>
          <form id="f">
            <div class="case-sec"><h2>Applicant</h2></div>
            <div class="form">
              <div class="field"><label>Customer ID or mobile</label><input id="cust" value="${esc(preset)}" required placeholder="e.g. 6 or 9810012345"></div>
              <div class="field"><label>&nbsp;</label><div id="custinfo" class="muted" style="height:40px;display:flex;align-items:center">Look up an existing customer.</div></div>
            </div>
            <input type="hidden" name="customerId">
            <div class="case-sec"><h2>Loan request</h2></div>
            ${applicationForm({})}
            <div class="form-actions"><button class="btn primary">${icon('check')}Create draft</button><a class="btn ghost" href="#/applications">Cancel</a>
              <span class="muted" style="font-size:13px">New to the bank? <a href="#/customers/new">Register the customer</a> first.</span></div>
          </form>
        </div>
        <div class="case-side">
          <div class="side-title">Preview</div>
          <div class="gauge-card"><div class="head"><span>FOIR</span><span class="muted" style="font-weight:500">limit ${esc(meta.rules.maxFoir)}%</span></div>
            <div class="gauge-wrap"><div data-gauge>${gauge(null, num(meta.rules.maxFoir))}</div><div class="val"><b data-foir>–</b><small data-emi>enter amount and tenure</small></div></div></div>
          <div class="gauge-card"><div class="head"><span>Eligibility checks</span></div><ul class="checks" data-checks></ul></div>
          <p class="muted" style="font-size:12px;margin:0">Indicative only. Final EMI and FOIR are worked out and stored when the draft is submitted.</p>
        </div>
      </div>`;
    const form = view.querySelector('#f');
    const custInput = view.querySelector('#cust');
    const info = view.querySelector('#custinfo');
    let timer = null;
    const update = () => {
      const f = formData(form);
      const rule = meta.rules.products.find(p => p.product === f.product);
      const hint = form.querySelector('[data-rulehint]');
      if (rule) hint.textContent = `₹${fmt(rule.minAmount, 0)} – ₹${fmt(rule.maxAmount, 0)} · ${rule.minTenure}–${rule.maxTenure} months · ${rule.annualRate}% p.a.`;
      const amount = num(f.requestedAmount), tenure = num(f.tenureMonths), income = num(f.monthlyIncome), obl = num(f.existingObligations);
      const checks = [];
      const add = (state, text) => checks.push(`<li class="${state}">${icon(state === 'bad' ? 'x' : (state === 'wait' ? 'clock' : 'check'))}${esc(text)}</li>`);
      if (customer && customer.age !== null) add(customer.age >= meta.rules.minAge && customer.age <= meta.rules.maxAge ? '' : 'bad', `Age ${customer.age} (${meta.rules.minAge}–${meta.rules.maxAge})`);
      else add('wait', 'Age, once a customer is selected');
      if (rule) {
        if (f.requestedAmount) add(amount >= rule.minAmount && amount <= rule.maxAmount ? '' : 'bad', `Amount within ₹${fmt(rule.minAmount, 0)}–₹${fmt(rule.maxAmount, 0)}`); else add('wait', 'Amount within product range');
        if (f.tenureMonths) add(Number.isInteger(tenure) && tenure >= rule.minTenure && tenure <= rule.maxTenure ? '' : 'bad', `Tenure within ${rule.minTenure}–${rule.maxTenure} months`); else add('wait', 'Tenure within product range');
        if (f.monthlyIncome) add(income >= rule.minIncome ? '' : 'bad', `Income at least ₹${fmt(rule.minIncome, 0)}`); else add('wait', 'Income at least the product minimum');
        if (!rule.coBorrowerAllowed && f.coBorrowerName) add('bad', 'No co-borrower on education loans');
      }
      const listEl = view.querySelector('[data-checks]');
      listEl.innerHTML = checks.join('');
      clearTimeout(timer);
      timer = setTimeout(async () => {
        if (!(amount > 0 && Number.isInteger(tenure) && tenure > 0)) return;
        try {
          const r = await api('GET', `calculator?product=${f.product}&amount=${amount}&tenure=${tenure}&income=${income || ''}&obligations=${obl || 0}`);
          view.querySelector('[data-emi]').textContent = `EMI ₹${r.emiText} at ${r.annualRate}%`;
          if (r.foirPercentage) {
            view.querySelector('[data-foir]').textContent = r.foirPercentage + '%';
            view.querySelector('[data-gauge]').innerHTML = gauge(r.foirPercentage, num(meta.rules.maxFoir));
            listEl.insertAdjacentHTML('beforeend', `<li class="${r.withinLimit ? '' : 'bad'}">${icon(r.withinLimit ? 'check' : 'x')}FOIR ${esc(r.foirPercentage)}% within ${esc(meta.rules.maxFoir)}%</li>`);
          }
        } catch (e) { /* half typed */ }
      }, 220);
    };
    form.addEventListener('input', update);
    form.addEventListener('change', update);
    const lookup = async () => {
      const v = custInput.value.trim();
      form.customerId.value = '';
      customer = null;
      if (v) {
        try {
          const list = await api('GET', 'customers?q=' + encodeURIComponent(v));
          if (list.length === 1) {
            customer = list[0];
            form.customerId.value = customer.customerId;
            info.innerHTML = `<div class="who">${avatar(customer.name, customer.customerId)}<div><b style="color:var(--text)">${esc(customer.name)}</b><span class="sub" style="display:block;font-size:12px">${esc(customer.age)} yrs · ${esc(customer.mobile)}</span></div></div>`;
          } else {
            info.textContent = list.length ? 'Several matches, use the customer ID.' : 'No customer with that ID or mobile.';
          }
        } catch (e) { info.textContent = e.message; }
      }
      update();
    };
    custInput.addEventListener('change', lookup);
    custInput.addEventListener('keydown', e => {
      if (e.key === 'Enter') { e.preventDefault(); lookup(); }
    });
    lookup();
    form.addEventListener('submit', async e => {
      e.preventDefault();
      if (!form.customerId.value) await lookup();
      try {
        const a = await api('POST', 'applications', formData(form));
        location.hash = '#/applications/' + a.applicationId + '?created=1';
      } catch (err) { showError(view.querySelector('#msg'), err); }
    });
  }

  function eligibilityRows(a, c) {
    const rule = meta.rules.products.find(p => p.product === a.product) || {};
    const failed = a.eligible === false && a.eligibilityReasons ? a.eligibilityReasons.split('; ') : [];
    const assessed = !!a.submittedAt;
    const res = re => !assessed ? '<span class="pill wait">Pending</span>'
      : (failed.some(f => re.test(f)) ? '<span class="pill bad">Failed</span>' : '<span class="pill ok">Passed</span>');
    return [
      ['Applicant age', c.age === null ? '–' : c.age + ' years', `${meta.rules.minAge} – ${meta.rules.maxAge} years`, res(/age|birth/i)],
      ['Amount requested', `<span class="amount">₹${esc(a.requestedAmountText)}</span>`, `₹${fmt(rule.minAmount, 0)} – ₹${fmt(rule.maxAmount, 0)}`, res(/amount/i)],
      ['Tenure', a.tenureMonths + ' months', `${rule.minTenure} – ${rule.maxTenure} months`, res(/tenure/i)],
      ['Monthly net income', '₹' + esc(a.monthlyIncomeText), `at least ₹${fmt(rule.minIncome, 0)}`, res(/income/i)],
      ['Co-borrower', esc(a.coBorrowerName || 'None'), rule.coBorrowerAllowed ? 'allowed' : 'not allowed', res(/co-borrower/i)],
      ['FOIR', assessed ? esc(a.foirPercentage) + '%' : '–', `at most ${esc(meta.rules.maxFoir)}%`, res(/FOIR/)]
    ];
  }

  function sideAction(a) {
    switch (a.status) {
      case 'DRAFT':
        return `<div class="side-action"><p class="note-line">Submitting runs every eligibility rule and stores EMI and FOIR. A failed rule rejects the application straight away.</p>
          <button class="btn primary big" data-act="submit">${icon('send')}Submit for assessment</button>
          <div class="row"><button class="btn" data-act="edit">${icon('pencil')}Edit draft</button><a class="btn" href="#/customers/${a.customerId}">${icon('user')}Customer</a></div></div>`;
      case 'UNDER_REVIEW':
      case 'CREDIT_APPROVAL': {
        const review = a.status === 'UNDER_REVIEW';
        return `<div class="side-action"><label for="reason">${review ? 'Reviewer remarks' : 'Credit remarks'} (required to reject)</label>
          <textarea id="reason" placeholder="${review ? 'e.g. salary slips verified' : 'e.g. sanctioned as requested'}"></textarea>
          <div class="row"><button class="btn danger" data-act="reject">${icon('x')}Reject</button><button class="btn primary" data-act="approve">${icon('check')}${review ? 'Approve' : 'Sanction'}</button></div></div>`;
      }
      case 'APPROVED':
        return `<div class="side-action"><label for="ref">UTR / reference (blank = auto)</label><input id="ref" placeholder="auto generated">
          <div style="height:10px"></div><button class="btn primary big" data-act="disburse">${icon('banknote')}Disburse ₹${esc(a.approvedAmountText)}</button></div>`;
      case 'DISBURSED':
        return `<div class="side-action"><p class="note-line">The core banking utility picks this up within seconds and closes the file.</p>
          <button class="btn primary big" data-act="push">${icon('landmark')}Push to core banking</button></div>`;
      case 'EXIT':
        return `<div class="side-action"><p class="note-line">Disbursed and recorded in NG_CORE_BANKING. Nothing left to do.</p>
          ${a.hasApplicationForm ? `<a class="btn primary big" target="_blank" href="/api/applications/${a.applicationId}/form">${icon('scroll-text')}Open application form</a>` : ''}</div>`;
      case 'REJECTED':
        return `<div class="side-action"><p class="note-line">Rejected. The customer can apply again for this product.</p>
          <a class="btn primary big" href="#/applications/new?customer=${a.customerId}">${icon('file-plus')}Start a new application</a></div>`;
      default:
        return '';
    }
  }

  async function applicationDetail(ref, params) {
    const d = await api('GET', 'applications/' + encodeURIComponent(ref));
    const a = d.application;
    const c = d.customer;
    const id = a.applicationId;
    const rate = a.annualInterestRate ? num(a.annualInterestRate) : num((meta.rules.products.find(p => p.product === a.product) || {}).annualRate);
    const sched = schedule(num(a.requestedAmount), rate, a.tenureMonths);
    const today = new Date(meta.businessDate + 'T00:00:00');
    const paidOn = d.disbursement ? parseWhen(d.disbursement.disbursedAt) : today;
    const firstEmi = new Date(paidOn.getFullYear(), paidOn.getMonth() + 1, 1);
    const bars = repaymentBars(sched, firstEmi, today);
    const hiNote = bars.kind === 'now' ? 'This month'
      : (bars.kind === 'first' ? `First EMI, ${bars.firstName}` : 'Last EMI (repaid)');
    const totalInterest = sched.reduce((s, x) => s + x.interest, 0);
    const foir = a.submittedAt ? num(a.foirPercentage) : null;
    const limit = num(meta.rules.maxFoir);
    const history = d.history.slice().reverse();

    const noteHtml = h => {
      const neg = /REJECT/.test(h.action);
      const sys = h.performedBy === 'SYSTEM';
      const move = h.oldStatus && h.oldStatus !== h.newStatus ? ' · ' + LABELS[h.oldStatus] + ' → ' + LABELS[h.newStatus] : '';
      return `<div class="note" data-kind="${sys ? 'system' : 'people'}"><span class="ico ${neg ? 'red' : (sys ? '' : 'green')}">${icon(ACTION_ICON[h.action] || 'history')}</span>
        <div style="min-width:0;flex:1"><div class="top"><span>${esc(h.performedBy + move)}</span><span class="ago">${esc(ago(h.performedAt))}</span></div>
        <div class="txt">${esc(ACTIONS[h.action] || h.action)}</div>${h.remarks ? `<div class="rem">${esc(h.remarks)}</div>` : ''}</div></div>`;
    };

    const decisions = [
      ['Reviewer', a.reviewerCode ? `${esc(a.reviewerCode)} · ${esc(when(a.decidedAt))}` : '–'],
      ['Credit approver', a.creditApproverCode ? `${esc(a.creditApproverCode)} · ${esc(when(a.creditDecidedAt))}` : '–'],
      [a.status === 'REJECTED' ? 'Rejection reason' : 'Remarks', esc(a.decisionReason || '–')],
      ['Sanctioned amount', a.approvedAmountText ? `<span class="amount">₹${esc(a.approvedAmountText)}</span>` : '–'],
      ['Disbursed', d.disbursement ? `<span class="amount">₹${esc(d.disbursement.amountText)}</span>` : '–'],
      ['Reference', d.disbursement ? esc(d.disbursement.reference) : '–'],
      ['Disbursed on', d.disbursement ? `${esc(when(d.disbursement.disbursedAt))} by ${esc(d.disbursement.disbursedBy)}` : '–'],
      ['Alerts', d.alerts.length ? d.alerts.map(al => `<span class="pill late">${esc(al.message)}</span>`).join(' ') : '–']
    ];

    view.innerHTML = `
      <div class="case">
        <div class="case-main">
          <div class="case-title">
            <a class="icon-sq" href="#/applications" title="Back">${icon('arrow-left')}</a>
            <h1>${esc(a.applicationNumber)}</h1>${pill(a.status)}
            <div class="tools">
              ${a.hasApplicationForm ? `<a class="icon-sq" target="_blank" href="/api/applications/${id}/form" title="Application form">${icon('scroll-text')}</a>` : ''}
              <button class="icon-sq" type="button" data-print title="Print">${icon('printer')}</button>
            </div>
          </div>
          <div class="case-meta">
            <div><span class="ring-ico blue">${icon('calendar')}</span><div><b>Created</b><small>${esc(when(a.createdAt))} · ${esc(a.createdBy)}</small></div></div>
            <div><span class="ring-ico green">${icon('user')}</span><div><b>Customer</b><small><a href="#/customers/${c.customerId}">${esc(c.name)}</a> · ${esc(c.mobile)}</small></div></div>
            <div><span class="ring-ico red">${icon('building-2')}</span><div><b>Branch</b><small>${esc(a.branchCode)} · ${esc(a.branchName)}</small></div></div>
          </div>
          <div id="msg" style="margin-top:16px">${params.get('created') ? alertBox('ok', 'Draft created. Check the details, then submit it for assessment.') : ''}</div>
          <div id="edit-area"></div>
          <div class="case-sec"><h2>Repayment schedule</h2><span class="chip-select">${a.product === 'PERSONAL' ? 'Personal' : 'Education'} · ${a.tenureMonths} months · ${rate}%</span></div>
          <div id="bars">${bars.html}</div>
          <div class="legend" style="margin:12px 0 0 40px;flex-wrap:wrap"><span class="s1"><i></i>${esc(hiNote)}</span><span class="s3"><i></i>Principal part of the EMI</span><span>EMI ₹${fmt(sched[0].emi)}</span><span>Total interest ₹${fmt(totalInterest)}</span>
            <span class="faint">${d.disbursement ? 'EMIs from the month after disbursement' : 'projected, EMIs would start next month'}</span></div>
          <div class="case-sec" style="margin-top:30px"><h2>Assessment</h2></div>
          <div class="card" style="box-shadow:none;border:1px solid var(--line)">
            <div class="tabs" id="dtabs"><button class="active" data-tab="elig">Eligibility checks</button><button data-tab="dec">Decisions &amp; payout</button></div>
            <div data-pane="elig">${table([
              { label: 'Check', key: 0 }, { label: 'This application', html: true, key: 1 }, { label: 'Rule', key: 2 }, { label: 'Result', html: true, key: 3 }
            ], eligibilityRows(a, c))}</div>
            <div data-pane="dec" hidden>${table([{ label: 'Item', key: 0 }, { label: 'Detail', html: true, key: 1 }], decisions)}</div>
          </div>
        </div>
        <div class="case-side">
          <div class="side-title">Timeline</div>
          <div class="field"><label>Show</label><select id="notefilter"><option value="">All activity</option><option value="people">People</option><option value="system">System</option></select></div>
          <div class="notes">${history.map(noteHtml).join('')}</div>
          <div class="side-title" style="margin-top:6px">Affordability</div>
          <div class="gauge-card"><div class="head"><span>FOIR</span><span class="muted" style="font-weight:500">limit ${limit}%</span></div>
            <div class="gauge-wrap"><div data-gauge>${gauge(foir, limit)}</div>
              <div class="val"><b data-foir>${foir === null ? '–' : foir.toFixed(2) + '%'}</b><small data-foir-sub>${foir === null ? 'calculated on submission'
                : (foir <= limit ? (limit - foir).toFixed(2) + '% below the limit' : (foir - limit).toFixed(2) + '% over the limit')}</small></div></div></div>
          <div style="margin-top:auto">${sideAction(a)}</div>
        </div>
      </div>`;

    if (foir === null && num(a.monthlyIncome) > 0) {
      api('GET', `calculator?product=${a.product}&amount=${num(a.requestedAmount)}&tenure=${a.tenureMonths}&income=${num(a.monthlyIncome)}&obligations=${num(a.existingObligations)}`)
        .then(r => {
          if (!r.foirPercentage) return;
          view.querySelector('[data-gauge]').innerHTML = gauge(r.foirPercentage, limit);
          view.querySelector('[data-foir]').textContent = r.foirPercentage + '%';
          view.querySelector('[data-foir-sub]').textContent = 'indicative, before submission';
        }).catch(() => {});
    }

    view.querySelectorAll('#bars .col').forEach(col => {
      col.addEventListener('mousemove', e => {
        view.querySelectorAll('#bars .col').forEach(x => x.classList.toggle('on', x === col));
        const g = bars.groups[Number(col.dataset.i)];
        showTip(e, `<b>${esc(g.title)}</b><div><span>Paid</span><span>₹${fmt(g.pay)}</span></div><div><span>Principal</span><span>₹${fmt(g.princ)}</span></div>
          <div><span>Interest</span><span>₹${fmt(g.interest)}</span></div><div><span>Balance after</span><span>₹${fmt(g.bal)}</span></div>`);
      });
      col.addEventListener('mouseleave', () => {
        hideTip();
        view.querySelectorAll('#bars .col').forEach(x => x.classList.toggle('on', Number(x.dataset.i) === bars.current));
      });
    });
    view.querySelectorAll('#dtabs button').forEach(b => b.addEventListener('click', () => {
      view.querySelectorAll('#dtabs button').forEach(x => x.classList.toggle('active', x === b));
      view.querySelectorAll('[data-pane]').forEach(p => { p.hidden = p.dataset.pane !== b.dataset.tab; });
    }));
    view.querySelector('#notefilter').addEventListener('change', e => {
      view.querySelectorAll('.note').forEach(n => { n.hidden = !!e.target.value && n.dataset.kind !== e.target.value; });
    });
    view.querySelector('[data-print]').addEventListener('click', () => window.print());

    const msg = view.querySelector('#msg');
    const run = async fn => {
      view.querySelectorAll('[data-act]').forEach(b => { b.disabled = true; });
      try {
        await fn();
      } catch (err) {
        showError(msg, err);
        view.querySelectorAll('[data-act]').forEach(b => { b.disabled = false; });
      }
    };
    const reload = async html => {
      await applicationDetail(id, new URLSearchParams());
      view.querySelector('#msg').innerHTML = html;
      refreshCounts();
    };
    view.querySelectorAll('[data-act]').forEach(btn => btn.addEventListener('click', () => {
      const act = btn.dataset.act;
      if (act === 'edit') return editDraft(a);
      run(async () => {
        if (act === 'submit') {
          const r = await api('POST', `applications/${id}/submit`);
          await reload(r.assessment.eligible
            ? alertBox('ok', `Eligible. EMI ₹${r.assessment.emiText}, FOIR ${r.assessment.foirPercentage}%. It is now in the review queue.`)
            : alertBox('err', 'Not eligible, so the application was rejected. Rules that failed:', r.assessment.failedRules));
        } else if (act === 'approve' || act === 'reject') {
          const path = a.status === 'UNDER_REVIEW' ? 'review' : 'credit';
          const r = await api('POST', `applications/${id}/${path}`, { decision: act.toUpperCase(), reason: view.querySelector('#reason').value });
          await reload(alertBox(r.status === 'REJECTED' ? 'info' : 'ok', r.status === 'REJECTED' ? 'Application rejected.'
            : (r.status === 'APPROVED' ? 'Sanctioned. The application form has been generated.' : 'Approved and passed to credit approval.')));
        } else if (act === 'disburse') {
          const r = await api('POST', `applications/${id}/disburse`, { reference: view.querySelector('#ref').value });
          await reload(`<div class="receipt"><h3>₹${esc(r.amountText)} disbursed</h3><ul class="kv">
            <li>Application<b>${esc(r.applicationNumber)}</b></li><li>Customer<b>${esc(r.customerName)}</b></li>
            <li>Amount<b>₹${esc(r.amountText)}</b></li><li>Reference<b>${esc(r.reference)}</b></li><li>Date<b>${esc(when(r.disbursedAt))}</b></li></ul></div>`);
        } else if (act === 'push') {
          const r = await api('POST', 'utilities/core-push');
          await reload(alertBox(r.errors.length ? 'err' : 'ok', `${r.pushed} application(s) pushed to NG_CORE_BANKING.`, r.errors));
        }
      });
    }));
  }

  function editDraft(a) {
    const area = view.querySelector('#edit-area');
    area.innerHTML = `<div id="emsg"></div><form class="card" id="ef" style="box-shadow:none;border:1px solid var(--line)">${cardHead('Edit draft')}
      <div class="card-body">${applicationForm(a)}<div class="form-actions"><button class="btn primary">Save draft</button><button type="button" class="btn ghost" id="cancel">Cancel</button></div></div></form>`;
    const form = area.querySelector('#ef');
    area.querySelector('#cancel').addEventListener('click', () => { area.innerHTML = ''; });
    form.addEventListener('submit', async e => {
      e.preventDefault();
      try {
        await api('PUT', 'applications/' + a.applicationId, formData(form));
        await applicationDetail(a.applicationId, new URLSearchParams());
        view.querySelector('#msg').innerHTML = alertBox('ok', 'Draft saved.');
      } catch (err) { showError(area.querySelector('#emsg'), err); }
    });
    form.scrollIntoView({ behavior: 'smooth' });
  }

  // ---------- reports ----------

  const REPORTS = [
    ['register', 'Application register'], ['pipeline', 'Product pipeline'], ['disbursements', 'Disbursements'],
    ['overdue', 'Overdue reviews'], ['no-disbursement', 'Customers not disbursed'], ['repeat', 'Repeat applicants']
  ];

  const isMoney = c => /Amount|Requested|Emi|Total/i.test(c);

  function reportCard(t, i) {
    const cols = t.columns.map((col, k) => ({ label: col, r: t.numeric[k] && !isMoney(col), html: true,
      value: r => t.numeric[k] && isMoney(col) ? `<span class="amount">₹${esc(r[k])}</span>` : esc(r[k]) }));
    return `<div class="card">${cardHead(t.title.replace(/^\d+[ab]?\.\s*/, ''),
      `<div style="display:flex;gap:6px"><button class="btn sm ghost" data-csv="${i}">${icon('download')}CSV</button><button class="btn sm ghost" data-print>${icon('printer')}Print</button></div>`)}
      ${table(cols, t.rows, { empty: 'No rows for these filters.' })}</div>`;
  }

  function toCsv(t) {
    const cell = v => /[",\n]/.test(String(v)) ? `"${String(v).replace(/"/g, '""')}"` : String(v);
    return [t.columns, ...t.rows].map(r => r.map(cell).join(',')).join('\r\n');
  }

  async function reports(params) {
    const name = params.get('r') || 'register';
    let filters = '';
    if (name === 'register') {
      const st = meta.statuses.filter(s => s !== 'CORE_BANKING_PUSH').map(s => `<option value="${s}" ${params.get('status') === s ? 'selected' : ''}>${LABELS[s]}</option>`).join('');
      filters = `<div class="field"><label>Product</label><select name="product"><option value="">Both</option>
        <option value="PERSONAL" ${params.get('product') === 'PERSONAL' ? 'selected' : ''}>Personal</option>
        <option value="EDUCATION" ${params.get('product') === 'EDUCATION' ? 'selected' : ''}>Education</option></select></div>
        <div class="field"><label>Status</label><select name="status"><option value="">Any status</option>${st}</select></div>`;
    } else if (name === 'disbursements') {
      const today = meta.businessDate;
      const period = params.get('period') || 'DAILY';
      filters = `<div class="field"><label>From</label><input type="date" name="from" value="${esc(params.get('from') || today.slice(0, 8) + '01')}"></div>
        <div class="field"><label>To</label><input type="date" name="to" value="${esc(params.get('to') || today)}"></div>
        <div class="field"><label>Group by</label><select name="period">${[['DAILY', 'Day'], ['MONTHLY', 'Month'], ['QUARTERLY', 'Quarter']]
          .map(([v, l]) => `<option value="${v}" ${v === period ? 'selected' : ''}>${l}</option>`).join('')}</select></div>`;
    }
    const qs = new URLSearchParams(params);
    qs.delete('r');
    const tables = await api('GET', `reports/${name}?${qs.toString()}`);
    let chart = '';
    if (name === 'disbursements' && tables[0].rows.length) {
      const rows = tables[0].rows;
      const max = niceMax(Math.max(...rows.map(r => num(r[2]))));
      chart = `<div class="card">${cardHead('Amount disbursed by period')}<div class="card-body"><div class="bars" id="rbars"><div class="yaxis"><span>${short(max)}</span><span>${short(max * 2 / 3)}</span><span>${short(max / 3)}</span><span>0</span></div>
        ${rows.map((r, i) => `<div class="col" data-i="${i}"><div class="track"><div class="fill" style="height:${(num(r[2]) / max * 100).toFixed(1)}%;background:var(--brand)"></div></div><span>${esc(r[0])}</span></div>`).join('')}</div></div></div>`;
    }
    view.innerHTML = `
      ${pageHead('Reports', 'Parameterised queries from reports.sql')}
      <div class="card">
        <div class="tabs">${REPORTS.map(([k, l]) => `<a href="#/reports?r=${k}" class="${k === name ? 'active' : ''}">${l}</a>`).join('')}</div>
        ${filters ? `<form class="filters" id="rf" style="border-bottom:0">${filters}<button class="btn primary">${icon('play')}Run report</button></form>` : '<div style="height:4px"></div>'}
      </div>
      ${chart}${tables.map(reportCard).join('')}`;
    const rf = view.querySelector('#rf');
    if (rf) {
      rf.addEventListener('submit', e => {
        e.preventDefault();
        const p = new URLSearchParams(formData(rf));
        p.set('r', name);
        location.hash = '#/reports?' + p.toString();
      });
    }
    view.querySelectorAll('#rbars .col').forEach(col => {
      col.addEventListener('mousemove', e => {
        const r = tables[0].rows[Number(col.dataset.i)];
        showTip(e, `<b>${esc(r[0])}</b><div><span>Loans</span><span>${esc(r[1])}</span></div><div><span>Amount</span><span>₹${esc(r[2])}</span></div>`);
      });
      col.addEventListener('mouseleave', hideTip);
    });
    view.querySelectorAll('[data-csv]').forEach(b => b.addEventListener('click', () => {
      const t = tables[Number(b.dataset.csv)];
      const link = document.createElement('a');
      link.href = URL.createObjectURL(new Blob([toCsv(t)], { type: 'text/csv' }));
      link.download = t.title.replace(/^\d+[ab]?\.\s*/, '').toLowerCase().replace(/[^a-z0-9]+/g, '_') + '_' + meta.businessDate + '.csv';
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(link.href), 2000);
    }));
    view.querySelectorAll('[data-print]').forEach(b => b.addEventListener('click', () => window.print()));
  }

  // ---------- utilities ----------

  function jobCard(key, title, sched, last, button, ic) {
    const lamp = !last ? '' : (last.result === 'SUCCESS' ? 'ok' : 'bad');
    return `<div class="card">${cardHead(title, `<span class="ring-ico">${icon(ic)}</span>`)}<div class="card-body">
      <ul class="kv dots">
        <li>Schedule<b>${sched}</b></li>
        <li>Last run<b>${last ? `<span class="lamp ${lamp}"></span>${esc(when(last.startedAt))}` : 'never'}</b></li>
        <li>Result<b>${last ? `${esc(last.result.toLowerCase())}, ${last.applicationsProcessed} processed${key === 'review-monitor' ? `, ${last.alertsCreated} alerts` : ''}` : '–'}</b></li>
        <li>${key === 'review-monitor' ? 'Output' : 'Target'}<b>${key === 'review-monitor' ? esc(last && last.outputFile ? last.outputFile : '–') : 'NG_CORE_BANKING'}</b></li>
      </ul>
      <div class="form-actions"><button class="btn primary" data-run="${key}">${icon('play')}${button}</button></div></div></div>`;
  }

  async function utilities() {
    const [runs, alerts, core, exportsList, sched] = await Promise.all([
      api('GET', 'utilities/runs'), api('GET', 'utilities/alerts'), api('GET', 'utilities/core-banking'),
      api('GET', 'utilities/exports'), api('GET', 'utilities/schedule')]);
    const lastReview = runs.find(r => r.utilityName === 'REVIEW_MONITOR');
    const lastPush = runs.find(r => r.utilityName === 'CORE_BANKING_PUSH');
    view.innerHTML = `
      ${pageHead('Utilities', 'Background jobs, their output and the rollback test')}
      <div id="msg"></div>
      <div class="grid g2 mb">
        ${jobCard('review-monitor', 'Overdue review monitor', `daily ${esc(sched.reviewTime)} · next ${esc(when(sched.nextReviewRun))}`, lastReview, 'Run utility now', 'clock')}
        ${jobCard('core-push', 'Core banking push', `every ${sched.pushIntervalSeconds} seconds`, lastPush, 'Run push now', 'landmark')}
      </div>
      <div class="grid g2 mb">
        <div class="card">${cardHead('Overdue alerts', `<span class="muted">${alerts.length}</span>`)}${table([
          { label: 'Application', html: true, value: x => `<span class="strong">${esc(x.applicationNumber)}</span>` },
          { label: 'Alert', key: 'message' }, { label: 'Raised', value: x => ago(x.createdAt) }],
          alerts, { link: x => '#/applications/' + x.applicationId, empty: 'No alerts raised.', emptyIcon: 'bell' })}</div>
        <div class="card">${cardHead('CSV exports')}${table([
          { label: 'File', html: true, value: f => `<a href="/api/utilities/exports/${encodeURIComponent(f.name)}">${icon('download')} ${esc(f.name)}</a>` },
          { label: 'Size', r: true, value: f => (f.size / 1024).toFixed(1) + ' KB' }], exportsList, { empty: 'No exports yet.', emptyIcon: 'download' })}</div>
      </div>
      <div class="card">${cardHead('NG_CORE_BANKING', `<span class="muted">${core.length} rows</span>`)}${table([
        { label: 'Core ID', key: 'coreId' }, { label: 'Application', html: true, value: r => `<span class="strong">${esc(r.applicationNumber)}</span>` },
        { label: 'Customer', html: true, value: r => `<div class="who">${avatar(r.customerName, r.customerId)}<div>${esc(r.customerName)}<span class="sub">born ${esc(r.dateOfBirth)}</span></div></div>` },
        { label: 'KYC', key: 'kycDetails' }, { label: 'Approved', html: true, value: r => '₹' + esc(r.approvedAmountText) },
        { label: 'Disbursed', html: true, value: r => `<span class="amount">₹${esc(r.disbursedAmountText)}</span>` }, { label: 'On', key: 'disbursedDate' }],
        core, { link: r => '#/applications/' + r.applicationId, empty: 'Nothing pushed yet.', emptyIcon: 'landmark' })}</div>
      <div class="card">${cardHead('Run log')}${table([
        { label: 'Run', key: 'runId' }, { label: 'Utility', value: r => r.utilityName === 'REVIEW_MONITOR' ? 'Review monitor' : 'Core push' },
        { label: 'Trigger', value: r => r.triggerType.toLowerCase() }, { label: 'Business date', key: 'businessDate' },
        { label: 'Started', value: r => when(r.startedAt) },
        { label: 'Result', html: true, value: r => `<span class="pill ${r.result === 'SUCCESS' ? 'ok' : 'bad'}">${esc(r.result.toLowerCase())}</span>` },
        { label: 'Processed', r: true, key: 'applicationsProcessed' }, { label: 'Alerts', r: true, key: 'alertsCreated' },
        { label: 'Error', key: 'errorMessage' }], runs, { empty: 'No runs yet.' })}</div>
      <div class="card">${cardHead('Rollback test', `<span class="ring-ico red">${icon('triangle-alert')}</span>`)}<div class="card-body">
        <p class="muted" style="margin:0 0 14px">Arms a deliberate failure inside the next transaction of the chosen kind. The whole operation must roll back and the application stays where it was.</p>
        <div class="form-actions" style="margin-top:0">
          <button class="btn" data-fail="SUBMISSION">Fail next submission</button><button class="btn" data-fail="DECISION">Fail next decision</button>
          <button class="btn" data-fail="DISBURSEMENT">Fail next disbursement</button><button class="btn ghost" data-fail="">Disarm</button></div></div></div>`;
    bindLinks(view);
    const msg = view.querySelector('#msg');
    view.querySelectorAll('[data-run]').forEach(b => b.addEventListener('click', async () => {
      b.disabled = true;
      try {
        const r = await api('POST', 'utilities/' + b.dataset.run);
        await utilities();
        view.querySelector('#msg').innerHTML = b.dataset.run === 'core-push'
          ? alertBox(r.errors.length ? 'err' : 'ok', `${r.pending} disbursed application(s) found, ${r.pushed} pushed.`, r.errors)
          : alertBox(r.result === 'SUCCESS' ? 'ok' : 'err', `${r.applicationsProcessed} pending review(s), ${r.alertsCreated} new alert(s). Exported ${r.outputFile || 'nothing'}.`);
        refreshCounts();
      } catch (err) { showError(msg, err); b.disabled = false; }
    }));
    view.querySelectorAll('[data-fail]').forEach(b => b.addEventListener('click', async () => {
      try {
        const r = await api('POST', 'utilities/simulate-failure', { point: b.dataset.fail || null });
        msg.innerHTML = alertBox('info', r.armed ? `Armed. The next ${r.armed.toLowerCase()} will fail and roll back.` : 'Disarmed.');
      } catch (err) { showError(msg, err); }
    }));
  }

  // ---------- settings ----------

  async function settings() {
    const rules = await api('GET', 'settings/rules');
    const productCard = p => `
      <form class="card" data-product="${p.product}">${cardHead(p.product === 'PERSONAL' ? 'Personal loan' : 'Education loan')}<div class="card-body">
        <div class="form">
          <div class="field"><label>Minimum amount (₹)</label><input name="minAmount" value="${esc(p.minAmount)}"></div>
          <div class="field"><label>Maximum amount (₹)</label><input name="maxAmount" value="${esc(p.maxAmount)}"></div>
          <div class="field"><label>Minimum tenure (months)</label><input name="minTenure" value="${esc(p.minTenure)}"></div>
          <div class="field"><label>Maximum tenure (months)</label><input name="maxTenure" value="${esc(p.maxTenure)}"></div>
          <div class="field"><label>Minimum income (₹)</label><input name="minIncome" value="${esc(p.minIncome)}"></div>
          <div class="field"><label>Interest rate (% p.a.)</label><input name="annualRate" value="${esc(p.annualRate)}"></div>
          <div class="field"><label>Co-borrower</label><select name="coBorrowerAllowed">
            <option value="true" ${p.coBorrowerAllowed ? 'selected' : ''}>Allowed</option><option value="false" ${p.coBorrowerAllowed ? '' : 'selected'}>Not allowed</option></select></div>
        </div><div class="form-actions"><button class="btn primary">${icon('check')}Save</button></div></div></form>`;
    view.innerHTML = `
      ${pageHead('Configuration', 'Changes apply to the next submission, no restart needed')}
      <div id="msg"></div>
      <div class="grid g2 mb">
        <form class="card" id="bd">${cardHead('Business date', `<span class="ring-ico">${icon('calendar')}</span>`)}<div class="card-body">
          <p class="muted" style="margin:0 0 14px">Ages, review ageing and every timestamp use this date.${meta.usingSystemDate ? '' : ' It is set manually right now.'}</p>
          <div class="field" style="max-width:240px"><label>Date</label><input type="date" name="date" value="${esc(meta.businessDate)}"></div>
          <div class="form-actions"><button class="btn primary">Set date</button><button type="button" class="btn ghost" id="reset">Back to today</button></div></div></form>
        <form class="card" id="common">${cardHead('Applicant limits', `<span class="ring-ico">${icon('gauge')}</span>`)}<div class="card-body">
          <div class="form three">
            <div class="field"><label>Minimum age</label><input name="minAge" value="${rules.minAge}"></div>
            <div class="field"><label>Maximum age</label><input name="maxAge" value="${rules.maxAge}"></div>
            <div class="field"><label>FOIR limit (%)</label><input name="maxFoir" value="${esc(rules.maxFoir)}"></div>
          </div><div class="form-actions"><button class="btn primary">${icon('check')}Save limits</button></div></div></form>
      </div>
      <div class="grid g2">${rules.products.map(productCard).join('')}</div>`;
    const msg = view.querySelector('#msg');
    const done = async text => {
      await refreshMeta();
      await settings();
      view.querySelector('#msg').innerHTML = alertBox('ok', text);
    };
    view.querySelector('#bd').addEventListener('submit', async e => {
      e.preventDefault();
      try { await api('PUT', 'settings/business-date', formData(e.target)); await done('Business date updated.'); } catch (err) { showError(msg, err); }
    });
    view.querySelector('#reset').addEventListener('click', async () => {
      try { await api('PUT', 'settings/business-date', { date: null }); await done('Business date reset to today.'); } catch (err) { showError(msg, err); }
    });
    view.querySelector('#common').addEventListener('submit', async e => {
      e.preventDefault();
      try { await api('PUT', 'settings/rules', formData(e.target)); await done('Limits saved.'); } catch (err) { showError(msg, err); }
    });
    view.querySelectorAll('form[data-product]').forEach(f => f.addEventListener('submit', async e => {
      e.preventDefault();
      try { await api('PUT', 'settings/rules/' + f.dataset.product, formData(f)); await done('Product rules saved.'); } catch (err) { showError(msg, err); }
    }));
  }

  // ---------- search ----------

  document.getElementById('goto').addEventListener('submit', async e => {
    e.preventDefault();
    const input = document.getElementById('goto-q');
    const q = input.value.trim();
    if (!q) return;
    input.blur();
    if (/^la\d+$/i.test(q)) location.hash = '#/applications/' + q.toUpperCase();
    else if (/^\d{10}$/.test(q)) {
      const list = await api('GET', 'customers?by=MOBILE&q=' + q).catch(() => []);
      location.hash = list.length === 1 ? '#/customers/' + list[0].customerId : '#/customers?by=MOBILE&q=' + q;
    } else if (/^\d+$/.test(q)) location.hash = '#/customers/' + q;
    else location.hash = '#/customers?by=NAME&q=' + encodeURIComponent(q);
    input.value = '';
  });
  document.addEventListener('keydown', e => {
    if (e.key === '/' && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName)) {
      e.preventDefault();
      document.getElementById('goto-q').focus();
    }
  });

  // ---------- router ----------

  async function route() {
    hideTip();
    menu.hidden = true;
    const hash = location.hash.replace(/^#/, '') || '/';
    const [path, query] = hash.split('?');
    const params = new URLSearchParams(query || '');
    const parts = path.split('/').filter(Boolean);
    let key = 'desk';
    let page;
    if (parts[0] === 'customers' && parts[1] === 'new') { key = 'customers'; page = () => newCustomer(); }
    else if (parts[0] === 'customers' && parts[1]) { key = 'customers'; page = () => customerDetail(parts[1], params); }
    else if (parts[0] === 'customers') { key = 'customers'; page = () => customers(params); }
    else if (parts[0] === 'applications' && parts[1] === 'new') { key = 'new-application'; page = () => newApplication(params); }
    else if (parts[0] === 'applications' && parts[1]) { key = 'applications'; page = () => applicationDetail(parts[1], params); }
    else if (parts[0] === 'applications') { key = 'applications'; page = () => applications(params); }
    else if (parts[0] === 'queue' && parts[1]) { key = 'queue-' + parts[1]; page = () => queue(parts[1]); }
    else if (parts[0] === 'reports') { key = 'reports'; page = () => reports(params); }
    else if (parts[0] === 'utilities') { key = 'utilities'; page = () => utilities(); }
    else if (parts[0] === 'settings') { key = 'settings'; page = () => settings(); }
    else { page = () => dashboard(); }

    document.querySelectorAll('.nav a').forEach(a => a.classList.toggle('active', a.dataset.route === key));
    try {
      if (!meta) await refreshMeta();
      await page();
    } catch (err) {
      view.innerHTML = pageHead('Something went wrong', '') + alertBox('err', err.message, err.details);
    }
    window.scrollTo(0, 0);
  }

  paintIcons(document);
  themeIcon();
  window.addEventListener('hashchange', route);
  route().then(refreshCounts);
})();
