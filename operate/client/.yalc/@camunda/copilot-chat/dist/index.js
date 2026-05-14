import { jsx as y, jsxs as j, Fragment as yn } from "react/jsx-runtime";
import * as W from "react";
import Mt, { createElement as Ci, Fragment as cr, useRef as Q, useReducer as Ti, useCallback as M, useContext as Si, forwardRef as Ri, useState as He, useEffect as se, createContext as Ni, useLayoutEffect as ur, useMemo as dr } from "react";
import { InlineNotification as pr, OperationalTag as mr, InlineLoading as Ai, IconButton as at, Button as rt, Link as ra, DismissibleTag as Mi, TextInput as Oi, SkeletonText as ia, Loading as Pi, Theme as Di } from "@carbon/react";
import sa from "@carbon/ai-chat-components/es/react/chat-button";
import { StopFilledAlt as ji, Send as Bi, AddLarge as zi, ChevronDown as hr, ChevronRight as fr, ChevronUp as Li, ChevronLeft as Fi, Launch as qi, Checkmark as $i, Close as Ui, Edit as Gi, TrashCan as Hi } from "@carbon/icons-react";
import Vi from "@carbon/ai-chat-components/es/react/processing.js";
import Ki from "@carbon/ai-chat-components/es/react/markdown";
import Wi from "@carbon/ai-chat-components/es/react/card";
import Xi from "@carbon/ai-chat-components/es/react/toolbar";
import Qi from "@carbon/ai-chat-components/es/react/chat-shell";
const Ji = "_themeWrapper_neun4_9", Yi = "_container_neun4_17", Zi = "_messagesSlot_neun4_29", es = "_workspaceSlot_neun4_36", ts = "_inputBlock_neun4_47", Ot = {
  themeWrapper: Ji,
  container: Yi,
  messagesSlot: Zi,
  workspaceSlot: es,
  inputBlock: ts
};
function Ne(e) {
  if (e === void 0) throw new ReferenceError("this hasn't been initialised - super() hasn't been called");
  return e;
}
function bn(e, t) {
  return bn = Object.setPrototypeOf ? Object.setPrototypeOf.bind() : function(n, r) {
    return n.__proto__ = r, n;
  }, bn(e, t);
}
function ns(e, t) {
  e.prototype = Object.create(t.prototype), e.prototype.constructor = e, bn(e, t);
}
var as = Object.defineProperty, rs = Object.defineProperties, is = Object.getOwnPropertyDescriptors, Yt = Object.getOwnPropertySymbols, gr = Object.prototype.hasOwnProperty, yr = Object.prototype.propertyIsEnumerable, vn = (e, t, n) => t in e ? as(e, t, { enumerable: !0, configurable: !0, writable: !0, value: n }) : e[t] = n, xe = (e, t) => {
  for (var n in t || (t = {})) gr.call(t, n) && vn(e, n, t[n]);
  if (Yt) for (var n of Yt(t)) yr.call(t, n) && vn(e, n, t[n]);
  return e;
}, br = (e, t) => rs(e, is(t)), ss = (e, t) => {
  var n = {};
  for (var r in e) gr.call(e, r) && t.indexOf(r) < 0 && (n[r] = e[r]);
  if (e != null && Yt) for (var r of Yt(e)) t.indexOf(r) < 0 && yr.call(e, r) && (n[r] = e[r]);
  return n;
}, ie = (e, t, n) => (vn(e, typeof t != "symbol" ? t + "" : t, n), n), wt = (e, t, n) => new Promise((r, a) => {
  var i = (l) => {
    try {
      o(n.next(l));
    } catch (c) {
      a(c);
    }
  }, s = (l) => {
    try {
      o(n.throw(l));
    } catch (c) {
      a(c);
    }
  }, o = (l) => l.done ? r(l.value) : Promise.resolve(l.value).then(i, s);
  o((n = n.apply(e, t)).next());
}), os = "hCaptcha-script", Qt = "hCaptchaOnLoad", oa = "script-error", nt = "@hCaptcha/loader";
function ls(e) {
  return Object.entries(e).filter(([, t]) => t || t === !1).map(([t, n]) => `${encodeURIComponent(t)}=${encodeURIComponent(String(n))}`).join("&");
}
function vr(e) {
  let t = e && e.ownerDocument || document, n = t.defaultView || t.parentWindow || window;
  return { document: t, window: n };
}
function kr(e) {
  return e || document.head;
}
function cs(e) {
  var t;
  e.setTag("source", nt), e.setTag("url", document.URL), e.setContext("os", { UA: navigator.userAgent }), e.setContext("browser", xe({}, us())), e.setContext("device", br(xe({}, ps()), { screen_width_pixels: screen.width, screen_height_pixels: screen.height, language: navigator.language, orientation: ((t = screen.orientation) == null ? void 0 : t.type) || "Unknown", processor_count: navigator.hardwareConcurrency, platform: navigator.platform }));
}
function us() {
  var e, t, n, r, a, i;
  let s = navigator.userAgent, o, l;
  return s.indexOf("Firefox") !== -1 ? (o = "Firefox", l = (e = s.match(/Firefox\/([\d.]+)/)) == null ? void 0 : e[1]) : s.indexOf("Edg") !== -1 ? (o = "Microsoft Edge", l = (t = s.match(/Edg\/([\d.]+)/)) == null ? void 0 : t[1]) : s.indexOf("Chrome") !== -1 && s.indexOf("Safari") !== -1 ? (o = "Chrome", l = (n = s.match(/Chrome\/([\d.]+)/)) == null ? void 0 : n[1]) : s.indexOf("Safari") !== -1 && s.indexOf("Chrome") === -1 ? (o = "Safari", l = (r = s.match(/Version\/([\d.]+)/)) == null ? void 0 : r[1]) : s.indexOf("Opera") !== -1 || s.indexOf("OPR") !== -1 ? (o = "Opera", l = (a = s.match(/(Opera|OPR)\/([\d.]+)/)) == null ? void 0 : a[2]) : s.indexOf("MSIE") !== -1 || s.indexOf("Trident") !== -1 ? (o = "Internet Explorer", l = (i = s.match(/(MSIE |rv:)([\d.]+)/)) == null ? void 0 : i[2]) : (o = "Unknown", l = "Unknown"), { name: o, version: l };
}
function ds(e) {
  return new Promise((t) => setTimeout(t, e));
}
function ps() {
  let e = navigator.userAgent, t;
  e.indexOf("Win") !== -1 ? t = "Windows" : e.indexOf("Mac") !== -1 ? t = "Mac" : e.indexOf("Linux") !== -1 ? t = "Linux" : e.indexOf("Android") !== -1 ? t = "Android" : e.indexOf("like Mac") !== -1 || e.indexOf("iPhone") !== -1 || e.indexOf("iPad") !== -1 ? t = "iOS" : t = "Unknown";
  let n;
  return /Mobile|iPhone|iPod|Android/i.test(e) ? n = "Mobile" : /Tablet|iPad/i.test(e) ? n = "Tablet" : n = "Desktop", { model: t, family: t, device: n };
}
var ms = class wr {
  constructor(t) {
    ie(this, "_parent"), ie(this, "breadcrumbs", []), ie(this, "context", {}), ie(this, "extra", {}), ie(this, "tags", {}), ie(this, "request"), ie(this, "user"), this._parent = t;
  }
  get parent() {
    return this._parent;
  }
  child() {
    return new wr(this);
  }
  setRequest(t) {
    return this.request = t, this;
  }
  removeRequest() {
    return this.request = void 0, this;
  }
  addBreadcrumb(t) {
    return typeof t.timestamp > "u" && (t.timestamp = (/* @__PURE__ */ new Date()).toISOString()), this.breadcrumbs.push(t), this;
  }
  setExtra(t, n) {
    return this.extra[t] = n, this;
  }
  removeExtra(t) {
    return delete this.extra[t], this;
  }
  setContext(t, n) {
    return typeof n.type > "u" && (n.type = t), this.context[t] = n, this;
  }
  removeContext(t) {
    return delete this.context[t], this;
  }
  setTags(t) {
    return this.tags = xe(xe({}, this.tags), t), this;
  }
  setTag(t, n) {
    return this.tags[t] = n, this;
  }
  removeTag(t) {
    return delete this.tags[t], this;
  }
  setUser(t) {
    return this.user = t, this;
  }
  removeUser() {
    return this.user = void 0, this;
  }
  toBody() {
    let t = [], n = this;
    for (; n; ) t.push(n), n = n.parent;
    return t.reverse().reduce((r, a) => {
      var i;
      return r.breadcrumbs = [...(i = r.breadcrumbs) != null ? i : [], ...a.breadcrumbs], r.extra = xe(xe({}, r.extra), a.extra), r.contexts = xe(xe({}, r.contexts), a.context), r.tags = xe(xe({}, r.tags), a.tags), a.user && (r.user = a.user), a.request && (r.request = a.request), r;
    }, { breadcrumbs: [], extra: {}, contexts: {}, tags: {}, request: void 0, user: void 0 });
  }
  clear() {
    this.breadcrumbs = [], this.context = {}, this.tags = {}, this.user = void 0;
  }
}, hs = /^\s*at (?:(.*?) ?\()?((?:file|https?|blob|chrome-extension|address|native|eval|webpack|<anonymous>|[-a-z]+:|.*bundle|\/).*?)(?::(\d+))?(?::(\d+))?\)?\s*$/i, fs = /^\s*(.*?)(?:\((.*?)\))?(?:^|@)?((?:file|https?|blob|chrome|webpack|resource|moz-extension).*?:\/.*?|\[native code\]|[^@]*(?:bundle|\d+\.js))(?::(\d+))?(?::(\d+))?\s*$/i, gs = /^\s*at (?:((?:\[object object\])?.+) )?\(?((?:file|ms-appx|https?|webpack|blob):.*?):(\d+)(?::(\d+))?\)?\s*$/i, ys = /^(?:(\w+):)\/\/(?:(\w+)(?::(\w+))?@)([\w.-]+)(?::(\d+))?\/(.+)/, $t = "?", la = "An unknown error occurred", bs = "0.0.4";
function vs(e) {
  for (let t = 0; t < e.length; t++) e[t] = Math.floor(Math.random() * 256);
  return e;
}
function pe(e) {
  return (e + 256).toString(16).substring(1);
}
function ks() {
  let e = vs(new Array(16));
  return e[6] = e[6] & 15 | 64, e[8] = e[8] & 63 | 128, pe(e[0]) + pe(e[1]) + pe(e[2]) + pe(e[3]) + "-" + pe(e[4]) + pe(e[5]) + "-" + pe(e[6]) + pe(e[7]) + "-" + pe(e[8]) + pe(e[9]) + "-" + pe(e[10]) + pe(e[11]) + pe(e[12]) + pe(e[13]) + pe(e[14]) + pe(e[15]);
}
var ws = [[hs, "chrome"], [gs, "winjs"], [fs, "gecko"]];
function xs(e) {
  var t, n, r, a;
  if (!e.stack) return null;
  let i = [], s = (r = (n = (t = e.stack).split) == null ? void 0 : n.call(t, `
`)) != null ? r : [];
  for (let o = 0; o < s.length; ++o) {
    let l = null, c = null, u = null;
    for (let [d, p] of ws) if (c = d.exec(s[o]), c) {
      u = p;
      break;
    }
    if (!(!c || !u)) {
      if (u === "chrome") l = { filename: (a = c[2]) != null && a.startsWith("address at ") ? c[2].substring(11) : c[2], function: c[1] || $t, lineno: c[3] ? +c[3] : null, colno: c[4] ? +c[4] : null };
      else if (u === "winjs") l = { filename: c[2], function: c[1] || $t, lineno: +c[3], colno: c[4] ? +c[4] : null };
      else if (u === "gecko") o === 0 && !c[5] && e.columnNumber !== void 0 && i.length > 0 && (i[0].column = e.columnNumber + 1), l = { filename: c[3], function: c[1] || $t, lineno: c[4] ? +c[4] : null, colno: c[5] ? +c[5] : null };
      else continue;
      !l.function && l.lineno && (l.function = $t), i.push(l);
    }
  }
  return i.length ? i.reverse() : null;
}
function Es(e) {
  let t = xs(e);
  return { type: e.name, value: e.message, stacktrace: { frames: t ?? [] } };
}
function _s(e) {
  let t = ys.exec(e), n = t ? t.slice(1) : [];
  if (n.length !== 6) throw new Error("Invalid DSN");
  let r = n[5].split("/"), a = r.slice(0, -1).join("/");
  return n[0] + "://" + n[3] + (n[4] ? ":" + n[4] : "") + (a ? "/" + a : "") + "/api/" + r.pop() + "/envelope/?sentry_version=7&sentry_key=" + n[1] + (n[2] ? "&sentry_secret=" + n[2] : "");
}
function Is(e, t, n) {
  var r, a;
  let i = xe({ event_id: ks().replaceAll("-", ""), platform: "javascript", sdk: { name: "@hcaptcha/sentry", version: bs }, environment: t, release: n, timestamp: Date.now() / 1e3 }, e.scope.toBody());
  if (e.type === "exception") {
    i.message = (a = (r = e.error) == null ? void 0 : r.message) != null ? a : "Unknown error", i.fingerprint = [i.message];
    let s = [], o = e.error;
    for (let l = 0; l < 5 && o && (s.push(Es(o)), !(!o.cause || !(o.cause instanceof Error))); l++) o = o.cause;
    i.exception = { values: s.reverse() };
  }
  return e.type === "message" && (i.message = e.message, i.level = e.level), i;
}
function Cs(e) {
  if (e instanceof Error) return e;
  if (typeof e == "string") return new Error(e);
  if (typeof e == "object" && e !== null && !Array.isArray(e)) {
    let n = e, { message: r } = n, a = ss(n, ["message"]), i = new Error(typeof r == "string" ? r : la);
    return Object.assign(i, a);
  }
  let t = new Error(la);
  return Object.assign(t, { cause: e });
}
function Ts(e, t, n) {
  return wt(this, null, function* () {
    var r, a;
    try {
      if (typeof fetch < "u" && typeof AbortSignal < "u") {
        let i;
        if (n) {
          let l = new AbortController();
          i = l.signal, setTimeout(() => l.abort(), n);
        }
        let s = yield fetch(e, br(xe({}, t), { signal: i })), o = yield s.text();
        return { status: s.status, body: o };
      }
      return yield new Promise((i, s) => {
        var o, l;
        let c = new XMLHttpRequest();
        if (c.open((o = t?.method) != null ? o : "GET", e), c.onload = () => i({ status: c.status, body: c.responseText }), c.onerror = () => s(new Error("XHR Network Error")), t?.headers) for (let [u, d] of Object.entries(t.headers)) c.setRequestHeader(u, d);
        if (n) {
          let u = setTimeout(() => {
            c.abort(), s(new Error("Request timed out"));
          }, n);
          c.onloadend = () => {
            clearTimeout(u);
          };
        }
        c.send((l = t?.body) == null ? void 0 : l.toString());
      });
    } catch (i) {
      return { status: 0, body: (a = (r = i?.toString) == null ? void 0 : r.call(i)) != null ? a : "Unknown error" };
    }
  });
}
var be, kn = (be = class {
  constructor(e) {
    ie(this, "apiURL"), ie(this, "dsn"), ie(this, "environment"), ie(this, "release"), ie(this, "sampleRate"), ie(this, "debug"), ie(this, "_scope"), ie(this, "shouldBuffer", !1), ie(this, "bufferlimit", 20), ie(this, "buffer", []);
    var t, n, r, a, i;
    this.environment = e.environment, this.release = e.release, this.sampleRate = (t = e.sampleRate) != null ? t : 1, this.debug = (n = e.debug) != null ? n : !1, this._scope = (r = e.scope) != null ? r : new ms(), this.apiURL = _s(e.dsn), this.dsn = e.dsn, this.shouldBuffer = (a = e.buffer) != null ? a : !1, this.bufferlimit = (i = e.bufferLimit) != null ? i : 20;
  }
  static init(e) {
    be._instance || (be._instance = new be(e));
  }
  static get instance() {
    if (!be._instance) throw new Error("Sentry has not been initialized");
    return be._instance;
  }
  log(...e) {
    this.debug && console.log(...e);
  }
  get scope() {
    return this._scope;
  }
  static get scope() {
    return be.instance.scope;
  }
  withScope(e) {
    let t = this._scope.child();
    e(t);
  }
  static withScope(e) {
    be.instance.withScope(e);
  }
  captureException(e, t) {
    this.captureEvent({ type: "exception", level: "error", error: Cs(e), scope: t ?? this._scope });
  }
  static captureException(e, t) {
    be.instance.captureException(e, t);
  }
  captureMessage(e, t = "info", n) {
    this.captureEvent({ type: "message", level: t, message: e, scope: n ?? this._scope });
  }
  static captureMessage(e, t = "info", n) {
    be.instance.captureMessage(e, t, n);
  }
  captureEvent(e) {
    if (Math.random() >= this.sampleRate) {
      this.log("Dropped event due to sample rate");
      return;
    }
    if (this.shouldBuffer) {
      if (this.buffer.length >= this.bufferlimit) return;
      this.buffer.push(e);
    } else this.sendEvent(e);
  }
  sendEvent(e, t = 5e3) {
    return wt(this, null, function* () {
      try {
        this.log("Sending sentry event", e);
        let n = Is(e, this.environment, this.release), r = { event_id: n.event_id, dsn: this.dsn }, a = { type: "event" }, i = JSON.stringify(r) + `
` + JSON.stringify(a) + `
` + JSON.stringify(n), s = yield Ts(this.apiURL, { method: "POST", headers: { "Content-Type": "application/x-sentry-envelope" }, body: i }, t);
        this.log("Sentry response", s.status), s.status !== 200 && (console.log(s.body), console.error("Failed to send event to Sentry", s));
      } catch (n) {
        console.error("Failed to send event", n);
      }
    });
  }
  flush(e = 5e3) {
    return wt(this, null, function* () {
      try {
        this.log("Flushing sentry events", this.buffer.length);
        let t = this.buffer.splice(0, this.buffer.length).map((n) => this.sendEvent(n, e));
        yield Promise.all(t), this.log("Flushed all events");
      } catch (t) {
        console.error("Failed to flush events", t);
      }
    });
  }
  static flush(e = 5e3) {
    return be.instance.flush(e);
  }
  static reset() {
    be._instance = void 0;
  }
}, ie(be, "_instance"), be), Ss = "https://d233059272824702afc8c43834c4912d@sentry.hcaptcha.com/6", Rs = "2.3.0", Ns = "production";
function As(e = !0) {
  if (!e) return ca();
  kn.init({ dsn: Ss, release: Rs, environment: Ns });
  let t = kn.scope;
  return cs(t), ca(t);
}
function ca(e = null) {
  return { addBreadcrumb: (t) => {
    e && e.addBreadcrumb(t);
  }, captureRequest: (t) => {
    e && e.setRequest(t);
  }, captureException: (t) => {
    e && kn.captureException(t, e);
  } };
}
function Ms({ scriptLocation: e, query: t, loadAsync: n = !0, crossOrigin: r = "anonymous", apihost: a = "https://js.hcaptcha.com", cleanup: i = !1, secureApi: s = !1, scriptSource: o = "" } = {}, l) {
  let c = kr(e), u = vr(c);
  return new Promise((d, p) => {
    let m = u.document.createElement("script");
    m.id = os, o ? m.src = `${o}?onload=${Qt}` : s ? m.src = `${a}/1/secure-api.js?onload=${Qt}` : m.src = `${a}/1/api.js?onload=${Qt}`, m.crossOrigin = r, m.async = n;
    let h = (g, v) => {
      try {
        !s && i && c.removeChild(m), v(g);
      } catch (k) {
        p(k);
      }
    };
    m.onload = (g) => h(g, d), m.onerror = (g) => {
      l && l(m.src), h(g, p);
    }, m.src += t !== "" ? `&${t}` : "", c.appendChild(m);
  });
}
var Ut = [];
function Os(e = { cleanup: !1 }, t) {
  try {
    t.addBreadcrumb({ category: nt, message: "hCaptcha loader params", data: e });
    let n = kr(e.scriptLocation), r = vr(n), a = Ut.find(({ scope: s }) => s === r.window);
    if (a) return t.addBreadcrumb({ category: nt, message: "hCaptcha already loaded" }), a.promise;
    let i = new Promise((s, o) => wt(this, null, function* () {
      try {
        r.window[Qt] = () => {
          t.addBreadcrumb({ category: nt, message: "hCaptcha script called onload function" }), s(r.window.hcaptcha);
        };
        let l = ls({ custom: e.custom, render: e.render, sentry: e.sentry, assethost: e.assethost, imghost: e.imghost, reportapi: e.reportapi, endpoint: e.endpoint, host: e.host, recaptchacompat: e.recaptchacompat, hl: e.hl, uj: e.uj });
        yield Ms(xe({ query: l }, e), (c) => {
          t.captureRequest({ url: c, method: "GET" });
        }), t.addBreadcrumb({ category: nt, message: "hCaptcha loaded", data: a });
      } catch {
        t.addBreadcrumb({ category: nt, message: "hCaptcha failed to load" });
        let c = Ut.findIndex((u) => u.scope === r.window);
        c !== -1 && Ut.splice(c, 1), o(new Error(oa));
      }
    }));
    return Ut.push({ promise: i, scope: r.window }), i;
  } catch (n) {
    return t.captureException(n), Promise.reject(new Error(oa));
  }
}
function xr(e, t, n = 0) {
  return wt(this, null, function* () {
    var r, a;
    let i = (r = e.maxRetries) != null ? r : 2, s = (a = e.retryDelay) != null ? a : 1e3, o = n < i ? "Retry loading hCaptcha Api" : "Exceeded maximum retries";
    try {
      return yield Os(e, t);
    } catch (l) {
      return t.addBreadcrumb({ category: nt, message: o }), n >= i ? (t.captureException(l), Promise.reject(l)) : (t.addBreadcrumb({ category: nt, message: `Waiting ${s}ms before retry attempt ${n + 1}` }), yield ds(s), n += 1, xr(e, t, n));
    }
  });
}
function Ps() {
  return wt(this, arguments, function* (e = {}) {
    let t = As(e.sentry);
    return yield xr(e, t);
  });
}
function ua(e) {
  var t = e && e.ownerDocument || document, n = t.defaultView || t.parentWindow || window;
  return {
    document: t,
    window: n
  };
}
function da(e) {
  return e || document.head;
}
var Ds = /* @__PURE__ */ (function(e) {
  ns(t, e);
  function t(r) {
    var a;
    return a = e.call(this, r) || this, a._hcaptcha = void 0, a.renderCaptcha = a.renderCaptcha.bind(Ne(a)), a.resetCaptcha = a.resetCaptcha.bind(Ne(a)), a.removeCaptcha = a.removeCaptcha.bind(Ne(a)), a.isReady = a.isReady.bind(Ne(a)), a._onReady = null, a.loadCaptcha = a.loadCaptcha.bind(Ne(a)), a.handleOnLoad = a.handleOnLoad.bind(Ne(a)), a.handleSubmit = a.handleSubmit.bind(Ne(a)), a.handleExpire = a.handleExpire.bind(Ne(a)), a.handleError = a.handleError.bind(Ne(a)), a.handleOpen = a.handleOpen.bind(Ne(a)), a.handleClose = a.handleClose.bind(Ne(a)), a.handleChallengeExpired = a.handleChallengeExpired.bind(Ne(a)), a.ref = /* @__PURE__ */ W.createRef(), a.apiScriptRequested = !1, a.sentryHub = null, a.captchaId = "", a._pendingExecute = null, a.state = {
      isApiReady: !1,
      isRemoved: !1,
      elementId: r.id
    }, a;
  }
  var n = t.prototype;
  return n.componentDidMount = function() {
    var a = this, i = da(this.props.scriptLocation), s = ua(i);
    this._hcaptcha = s.window.hcaptcha || void 0;
    var o = typeof this._hcaptcha < "u";
    if (o) {
      this.setState({
        isApiReady: !0
      }, function() {
        a.renderCaptcha();
      });
      return;
    }
    this.loadCaptcha();
  }, n.componentWillUnmount = function() {
    var a = this._hcaptcha, i = this.captchaId;
    this._cancelPendingExecute("react-component-unmounted"), this.isReady() && (a.reset(i), a.remove(i));
  }, n.shouldComponentUpdate = function(a, i) {
    return !(this.state.isApiReady !== i.isApiReady || this.state.isRemoved !== i.isRemoved);
  }, n.componentDidUpdate = function(a) {
    var i = this, s = ["sitekey", "size", "theme", "tabindex", "languageOverride", "endpoint"], o = s.every(function(l) {
      return a[l] === i.props[l];
    });
    o || this.removeCaptcha(function() {
      i.renderCaptcha();
    });
  }, n.loadCaptcha = function() {
    if (!this.apiScriptRequested) {
      var a = this.props, i = a.apihost, s = a.assethost, o = a.endpoint, l = a.host, c = a.imghost, u = a.languageOverride, d = a.reCaptchaCompat, p = a.reportapi, m = a.sentry, h = a.custom, g = a.loadAsync, v = a.scriptLocation, k = a.scriptSource, f = a.secureApi, w = a.cleanup, E = w === void 0 ? !0 : w, x = a.userJourneys, b = {
        render: "explicit",
        apihost: i,
        assethost: s,
        endpoint: o,
        hl: u,
        host: l,
        imghost: c,
        recaptchacompat: d === !1 ? "off" : null,
        reportapi: p,
        sentry: m,
        custom: h,
        loadAsync: g,
        scriptLocation: v,
        scriptSource: k,
        secureApi: f,
        cleanup: E,
        uj: x !== void 0 ? x : !1
      };
      Ps(b).then(this.handleOnLoad, this.handleError).catch(this.handleError), this.apiScriptRequested = !0;
    }
  }, n.renderCaptcha = function(a) {
    var i = this, s = this.props.onReady, o = this.state.isApiReady, l = this.captchaId;
    if (!(!o || l)) {
      var c = Object.assign({
        "open-callback": this.handleOpen,
        "close-callback": this.handleClose,
        "error-callback": this.handleError,
        "chalexpired-callback": this.handleChallengeExpired,
        "expired-callback": this.handleExpire,
        callback: this.handleSubmit
      }, this.props, {
        hl: this.props.hl || this.props.languageOverride,
        languageOverride: void 0
      }), u = this._hcaptcha, d = u.render(this.ref.current, c);
      this.captchaId = d, this.setState({
        isRemoved: !1
      }, function() {
        a && a(), s && s(), i._onReady && i._onReady(d);
      });
    }
  }, n.resetCaptcha = function() {
    var a = this._hcaptcha, i = this.captchaId;
    this.isReady() && (a.reset(i), this._cancelPendingExecute("hcaptcha-reset"));
  }, n.removeCaptcha = function(a) {
    var i = this, s = this._hcaptcha, o = this.captchaId;
    this._cancelPendingExecute("hcaptcha-removed"), this.isReady() && this.setState({
      isRemoved: !0
    }, function() {
      i.captchaId = "", s.remove(o), a && a();
    });
  }, n.handleOnLoad = function() {
    var a = this;
    this.setState({
      isApiReady: !0
    }, function() {
      var i = da(a.props.scriptLocation), s = ua(i);
      a._hcaptcha = s.window.hcaptcha, a.renderCaptcha(function() {
        var o = a.props.onLoad;
        o && o();
      });
    });
  }, n.handleSubmit = function(a) {
    var i = this.props.onVerify, s = this.state.isRemoved, o = this._hcaptcha, l = this.captchaId;
    if (!(typeof o > "u" || s)) {
      var c = o.getResponse(l), u = o.getRespKey(l);
      i && i(c, u);
    }
  }, n.handleExpire = function() {
    var a = this.props.onExpire, i = this._hcaptcha, s = this.captchaId;
    this.isReady() && (i.reset(s), a && a());
  }, n.handleError = function(a) {
    var i = this.props.onError, s = this._hcaptcha, o = this.captchaId;
    this.isReady() && s.reset(o), i && i(a);
  }, n.isReady = function() {
    var a = this.state, i = a.isApiReady, s = a.isRemoved;
    return i && !s;
  }, n._cancelPendingExecute = function(a) {
    if (this._pendingExecute) {
      var i = this._pendingExecute;
      this._pendingExecute = null;
      var s = new Error(a);
      i.reject(s);
    }
  }, n.handleOpen = function() {
    !this.isReady() || !this.props.onOpen || this.props.onOpen();
  }, n.handleClose = function() {
    !this.isReady() || !this.props.onClose || this.props.onClose();
  }, n.handleChallengeExpired = function() {
    !this.isReady() || !this.props.onChalExpired || this.props.onChalExpired();
  }, n.execute = function(a) {
    var i = this;
    a === void 0 && (a = null), a = typeof a == "object" ? a : null;
    try {
      var s = this._hcaptcha, o = this.captchaId;
      if (a && a.async && this._pendingExecute && this._cancelPendingExecute("hcaptcha-execute-replaced"), !this.isReady())
        return a && a.async ? new Promise(function(c, u) {
          i._pendingExecute = {
            resolve: c,
            reject: u
          }, i._onReady = function(d) {
            if (i._pendingExecute)
              try {
                var p = s.execute(d, a);
                p && typeof p.then == "function" ? p.then(function(m) {
                  i._pendingExecute = null, c(m);
                }).catch(function(m) {
                  i._pendingExecute = null, u(m);
                }) : (i._pendingExecute = null, u(new Error("hcaptcha-execute-no-promise")));
              } catch (m) {
                i._pendingExecute = null, u(m);
              }
          };
        }) : (this._onReady = function(c) {
          s.execute(c, a);
        }, null);
      var l = s.execute(o, a);
      return a && a.async && l && typeof l.then == "function" ? new Promise(function(c, u) {
        i._pendingExecute = {
          resolve: c,
          reject: u
        }, l.then(function(d) {
          i._pendingExecute = null, c(d);
        }).catch(function(d) {
          i._pendingExecute = null, u(d);
        });
      }) : l;
    } catch (c) {
      return a && a.async ? Promise.reject(c) : null;
    }
  }, n.close = function() {
    var a = this._hcaptcha, i = this.captchaId;
    if (this._cancelPendingExecute("hcaptcha-closed"), !!this.isReady())
      return a.close(i);
  }, n.setData = function(a) {
    var i = this._hcaptcha, s = this.captchaId;
    this.isReady() && (a && typeof a != "object" && (a = null), i.setData(s, a));
  }, n.getResponse = function() {
    var a = this._hcaptcha;
    return a.getResponse(this.captchaId);
  }, n.getRespKey = function() {
    var a = this._hcaptcha;
    return a.getRespKey(this.captchaId);
  }, n.render = function() {
    var a = this.state.elementId;
    return /* @__PURE__ */ W.createElement("div", {
      ref: this.ref,
      id: a
    });
  }, t;
})(W.Component);
const js = ({ children: e }) => Ci(cr, null, e), Bs = () => ({
  data: void 0,
  getData: async () => ({
    visitorId: void 0
  })
});
var Bt = class {
  constructor() {
    this.listeners = /* @__PURE__ */ new Set(), this.subscribe = this.subscribe.bind(this);
  }
  subscribe(e) {
    return this.listeners.add(e), this.onSubscribe(), () => {
      this.listeners.delete(e), this.onUnsubscribe();
    };
  }
  hasListeners() {
    return this.listeners.size > 0;
  }
  onSubscribe() {
  }
  onUnsubscribe() {
  }
}, zs = {
  // We need the wrapper function syntax below instead of direct references to
  // global setTimeout etc.
  //
  // BAD: `setTimeout: setTimeout`
  // GOOD: `setTimeout: (cb, delay) => setTimeout(cb, delay)`
  //
  // If we use direct references here, then anything that wants to spy on or
  // replace the global setTimeout (like tests) won't work since we'll already
  // have a hard reference to the original implementation at the time when this
  // file was imported.
  setTimeout: (e, t) => setTimeout(e, t),
  clearTimeout: (e) => clearTimeout(e),
  setInterval: (e, t) => setInterval(e, t),
  clearInterval: (e) => clearInterval(e)
}, Ls = class {
  // We cannot have TimeoutManager<T> as we must instantiate it with a concrete
  // type at app boot; and if we leave that type, then any new timer provider
  // would need to support ReturnType<typeof setTimeout>, which is infeasible.
  //
  // We settle for type safety for the TimeoutProvider type, and accept that
  // this class is unsafe internally to allow for extension.
  #e = zs;
  #n = !1;
  setTimeoutProvider(e) {
    process.env.NODE_ENV !== "production" && this.#n && e !== this.#e && console.error(
      "[timeoutManager]: Switching provider after calls to previous provider might result in unexpected behavior.",
      { previous: this.#e, provider: e }
    ), this.#e = e, process.env.NODE_ENV !== "production" && (this.#n = !1);
  }
  setTimeout(e, t) {
    return process.env.NODE_ENV !== "production" && (this.#n = !0), this.#e.setTimeout(e, t);
  }
  clearTimeout(e) {
    this.#e.clearTimeout(e);
  }
  setInterval(e, t) {
    return process.env.NODE_ENV !== "production" && (this.#n = !0), this.#e.setInterval(e, t);
  }
  clearInterval(e) {
    this.#e.clearInterval(e);
  }
}, wn = new Ls();
function Fs(e) {
  setTimeout(e, 0);
}
var sn = typeof window > "u" || "Deno" in globalThis;
function Ae() {
}
function qs(e, t) {
  return typeof e == "function" ? e(t) : e;
}
function $s(e) {
  return typeof e == "number" && e >= 0 && e !== 1 / 0;
}
function Us(e, t) {
  return Math.max(e + (t || 0) - Date.now(), 0);
}
function xn(e, t) {
  return typeof e == "function" ? e(t) : e;
}
function Gs(e, t) {
  return typeof e == "function" ? e(t) : e;
}
function pa(e, t) {
  const {
    type: n = "all",
    exact: r,
    fetchStatus: a,
    predicate: i,
    queryKey: s,
    stale: o
  } = e;
  if (s) {
    if (r) {
      if (t.queryHash !== Fn(s, t.options))
        return !1;
    } else if (!jt(t.queryKey, s))
      return !1;
  }
  if (n !== "all") {
    const l = t.isActive();
    if (n === "active" && !l || n === "inactive" && l)
      return !1;
  }
  return !(typeof o == "boolean" && t.isStale() !== o || a && a !== t.state.fetchStatus || i && !i(t));
}
function ma(e, t) {
  const { exact: n, status: r, predicate: a, mutationKey: i } = e;
  if (i) {
    if (!t.options.mutationKey)
      return !1;
    if (n) {
      if (ut(t.options.mutationKey) !== ut(i))
        return !1;
    } else if (!jt(t.options.mutationKey, i))
      return !1;
  }
  return !(r && t.state.status !== r || a && !a(t));
}
function Fn(e, t) {
  return (t?.queryKeyHashFn || ut)(e);
}
function ut(e) {
  return JSON.stringify(
    e,
    (t, n) => _n(n) ? Object.keys(n).sort().reduce((r, a) => (r[a] = n[a], r), {}) : n
  );
}
function jt(e, t) {
  return e === t ? !0 : typeof e != typeof t ? !1 : e && t && typeof e == "object" && typeof t == "object" ? Object.keys(t).every((n) => jt(e[n], t[n])) : !1;
}
var Hs = Object.prototype.hasOwnProperty;
function En(e, t, n = 0) {
  if (e === t)
    return e;
  if (n > 500) return t;
  const r = ha(e) && ha(t);
  if (!r && !(_n(e) && _n(t))) return t;
  const i = (r ? e : Object.keys(e)).length, s = r ? t : Object.keys(t), o = s.length, l = r ? new Array(o) : {};
  let c = 0;
  for (let u = 0; u < o; u++) {
    const d = r ? u : s[u], p = e[d], m = t[d];
    if (p === m) {
      l[d] = p, (r ? u < i : Hs.call(e, d)) && c++;
      continue;
    }
    if (p === null || m === null || typeof p != "object" || typeof m != "object") {
      l[d] = m;
      continue;
    }
    const h = En(p, m, n + 1);
    l[d] = h, h === p && c++;
  }
  return i === o && c === i ? e : l;
}
function Vs(e, t) {
  if (!t || Object.keys(e).length !== Object.keys(t).length)
    return !1;
  for (const n in e)
    if (e[n] !== t[n])
      return !1;
  return !0;
}
function ha(e) {
  return Array.isArray(e) && e.length === Object.keys(e).length;
}
function _n(e) {
  if (!fa(e))
    return !1;
  const t = e.constructor;
  if (t === void 0)
    return !0;
  const n = t.prototype;
  return !(!fa(n) || !n.hasOwnProperty("isPrototypeOf") || Object.getPrototypeOf(e) !== Object.prototype);
}
function fa(e) {
  return Object.prototype.toString.call(e) === "[object Object]";
}
function Ks(e) {
  return new Promise((t) => {
    wn.setTimeout(t, e);
  });
}
function Ws(e, t, n) {
  if (typeof n.structuralSharing == "function")
    return n.structuralSharing(e, t);
  if (n.structuralSharing !== !1) {
    if (process.env.NODE_ENV !== "production")
      try {
        return En(e, t);
      } catch (r) {
        throw console.error(
          `Structural sharing requires data to be JSON serializable. To fix this, turn off structuralSharing or return JSON-serializable data from your queryFn. [${n.queryHash}]: ${r}`
        ), r;
      }
    return En(e, t);
  }
  return t;
}
function Xs(e, t, n = 0) {
  const r = [...e, t];
  return n && r.length > n ? r.slice(1) : r;
}
function Qs(e, t, n = 0) {
  const r = [t, ...e];
  return n && r.length > n ? r.slice(0, -1) : r;
}
var Zt = /* @__PURE__ */ Symbol();
function Er(e, t) {
  return process.env.NODE_ENV !== "production" && e.queryFn === Zt && console.error(
    `Attempted to invoke queryFn when set to skipToken. This is likely a configuration error. Query hash: '${e.queryHash}'`
  ), !e.queryFn && t?.initialPromise ? () => t.initialPromise : !e.queryFn || e.queryFn === Zt ? () => Promise.reject(new Error(`Missing queryFn: '${e.queryHash}'`)) : e.queryFn;
}
function Js(e, t) {
  return typeof e == "function" ? e(...t) : !!e;
}
function Ys(e, t, n) {
  let r = !1, a;
  return Object.defineProperty(e, "signal", {
    enumerable: !0,
    get: () => (a ??= t(), r || (r = !0, a.aborted ? n() : a.addEventListener("abort", n, { once: !0 })), a)
  }), e;
}
var Zs = class extends Bt {
  #e;
  #n;
  #t;
  constructor() {
    super(), this.#t = (e) => {
      if (!sn && window.addEventListener) {
        const t = () => e();
        return window.addEventListener("visibilitychange", t, !1), () => {
          window.removeEventListener("visibilitychange", t);
        };
      }
    };
  }
  onSubscribe() {
    this.#n || this.setEventListener(this.#t);
  }
  onUnsubscribe() {
    this.hasListeners() || (this.#n?.(), this.#n = void 0);
  }
  setEventListener(e) {
    this.#t = e, this.#n?.(), this.#n = e((t) => {
      typeof t == "boolean" ? this.setFocused(t) : this.onFocus();
    });
  }
  setFocused(e) {
    this.#e !== e && (this.#e = e, this.onFocus());
  }
  onFocus() {
    const e = this.isFocused();
    this.listeners.forEach((t) => {
      t(e);
    });
  }
  isFocused() {
    return typeof this.#e == "boolean" ? this.#e : globalThis.document?.visibilityState !== "hidden";
  }
}, _r = new Zs();
function eo() {
  let e, t;
  const n = new Promise((a, i) => {
    e = a, t = i;
  });
  n.status = "pending", n.catch(() => {
  });
  function r(a) {
    Object.assign(n, a), delete n.resolve, delete n.reject;
  }
  return n.resolve = (a) => {
    r({
      status: "fulfilled",
      value: a
    }), e(a);
  }, n.reject = (a) => {
    r({
      status: "rejected",
      reason: a
    }), t(a);
  }, n;
}
var to = Fs;
function no() {
  let e = [], t = 0, n = (o) => {
    o();
  }, r = (o) => {
    o();
  }, a = to;
  const i = (o) => {
    t ? e.push(o) : a(() => {
      n(o);
    });
  }, s = () => {
    const o = e;
    e = [], o.length && a(() => {
      r(() => {
        o.forEach((l) => {
          n(l);
        });
      });
    });
  };
  return {
    batch: (o) => {
      let l;
      t++;
      try {
        l = o();
      } finally {
        t--, t || s();
      }
      return l;
    },
    /**
     * All calls to the wrapped function will be batched.
     */
    batchCalls: (o) => (...l) => {
      i(() => {
        o(...l);
      });
    },
    schedule: i,
    /**
     * Use this method to set a custom notify function.
     * This can be used to for example wrap notifications with `React.act` while running tests.
     */
    setNotifyFunction: (o) => {
      n = o;
    },
    /**
     * Use this method to set a custom function to batch notifications together into a single tick.
     * By default React Query will use the batch function provided by ReactDOM or React Native.
     */
    setBatchNotifyFunction: (o) => {
      r = o;
    },
    setScheduler: (o) => {
      a = o;
    }
  };
}
var ue = no(), ao = class extends Bt {
  #e = !0;
  #n;
  #t;
  constructor() {
    super(), this.#t = (e) => {
      if (!sn && window.addEventListener) {
        const t = () => e(!0), n = () => e(!1);
        return window.addEventListener("online", t, !1), window.addEventListener("offline", n, !1), () => {
          window.removeEventListener("online", t), window.removeEventListener("offline", n);
        };
      }
    };
  }
  onSubscribe() {
    this.#n || this.setEventListener(this.#t);
  }
  onUnsubscribe() {
    this.hasListeners() || (this.#n?.(), this.#n = void 0);
  }
  setEventListener(e) {
    this.#t = e, this.#n?.(), this.#n = e(this.setOnline.bind(this));
  }
  setOnline(e) {
    this.#e !== e && (this.#e = e, this.listeners.forEach((n) => {
      n(e);
    }));
  }
  isOnline() {
    return this.#e;
  }
}, en = new ao();
function ro(e) {
  return Math.min(1e3 * 2 ** e, 3e4);
}
function Ir(e) {
  return (e ?? "online") === "online" ? en.isOnline() : !0;
}
var In = class extends Error {
  constructor(e) {
    super("CancelledError"), this.revert = e?.revert, this.silent = e?.silent;
  }
};
function Cr(e) {
  let t = !1, n = 0, r;
  const a = eo(), i = () => a.status !== "pending", s = (g) => {
    if (!i()) {
      const v = new In(g);
      p(v), e.onCancel?.(v);
    }
  }, o = () => {
    t = !0;
  }, l = () => {
    t = !1;
  }, c = () => _r.isFocused() && (e.networkMode === "always" || en.isOnline()) && e.canRun(), u = () => Ir(e.networkMode) && e.canRun(), d = (g) => {
    i() || (r?.(), a.resolve(g));
  }, p = (g) => {
    i() || (r?.(), a.reject(g));
  }, m = () => new Promise((g) => {
    r = (v) => {
      (i() || c()) && g(v);
    }, e.onPause?.();
  }).then(() => {
    r = void 0, i() || e.onContinue?.();
  }), h = () => {
    if (i())
      return;
    let g;
    const v = n === 0 ? e.initialPromise : void 0;
    try {
      g = v ?? e.fn();
    } catch (k) {
      g = Promise.reject(k);
    }
    Promise.resolve(g).then(d).catch((k) => {
      if (i())
        return;
      const f = e.retry ?? (sn ? 0 : 3), w = e.retryDelay ?? ro, E = typeof w == "function" ? w(n, k) : w, x = f === !0 || typeof f == "number" && n < f || typeof f == "function" && f(n, k);
      if (t || !x) {
        p(k);
        return;
      }
      n++, e.onFail?.(n, k), Ks(E).then(() => c() ? void 0 : m()).then(() => {
        t ? p(k) : h();
      });
    });
  };
  return {
    promise: a,
    status: () => a.status,
    cancel: s,
    continue: () => (r?.(), a),
    cancelRetry: o,
    continueRetry: l,
    canStart: u,
    start: () => (u() ? h() : m().then(h), a)
  };
}
var Tr = class {
  #e;
  destroy() {
    this.clearGcTimeout();
  }
  scheduleGc() {
    this.clearGcTimeout(), $s(this.gcTime) && (this.#e = wn.setTimeout(() => {
      this.optionalRemove();
    }, this.gcTime));
  }
  updateGcTime(e) {
    this.gcTime = Math.max(
      this.gcTime || 0,
      e ?? (sn ? 1 / 0 : 300 * 1e3)
    );
  }
  clearGcTimeout() {
    this.#e && (wn.clearTimeout(this.#e), this.#e = void 0);
  }
}, io = class extends Tr {
  #e;
  #n;
  #t;
  #r;
  #a;
  #s;
  #o;
  constructor(e) {
    super(), this.#o = !1, this.#s = e.defaultOptions, this.setOptions(e.options), this.observers = [], this.#r = e.client, this.#t = this.#r.getQueryCache(), this.queryKey = e.queryKey, this.queryHash = e.queryHash, this.#e = ya(this.options), this.state = e.state ?? this.#e, this.scheduleGc();
  }
  get meta() {
    return this.options.meta;
  }
  get promise() {
    return this.#a?.promise;
  }
  setOptions(e) {
    if (this.options = { ...this.#s, ...e }, this.updateGcTime(this.options.gcTime), this.state && this.state.data === void 0) {
      const t = ya(this.options);
      t.data !== void 0 && (this.setState(
        ga(t.data, t.dataUpdatedAt)
      ), this.#e = t);
    }
  }
  optionalRemove() {
    !this.observers.length && this.state.fetchStatus === "idle" && this.#t.remove(this);
  }
  setData(e, t) {
    const n = Ws(this.state.data, e, this.options);
    return this.#i({
      data: n,
      type: "success",
      dataUpdatedAt: t?.updatedAt,
      manual: t?.manual
    }), n;
  }
  setState(e, t) {
    this.#i({ type: "setState", state: e, setStateOptions: t });
  }
  cancel(e) {
    const t = this.#a?.promise;
    return this.#a?.cancel(e), t ? t.then(Ae).catch(Ae) : Promise.resolve();
  }
  destroy() {
    super.destroy(), this.cancel({ silent: !0 });
  }
  reset() {
    this.destroy(), this.setState(this.#e);
  }
  isActive() {
    return this.observers.some(
      (e) => Gs(e.options.enabled, this) !== !1
    );
  }
  isDisabled() {
    return this.getObserversCount() > 0 ? !this.isActive() : this.options.queryFn === Zt || this.state.dataUpdateCount + this.state.errorUpdateCount === 0;
  }
  isStatic() {
    return this.getObserversCount() > 0 ? this.observers.some(
      (e) => xn(e.options.staleTime, this) === "static"
    ) : !1;
  }
  isStale() {
    return this.getObserversCount() > 0 ? this.observers.some(
      (e) => e.getCurrentResult().isStale
    ) : this.state.data === void 0 || this.state.isInvalidated;
  }
  isStaleByTime(e = 0) {
    return this.state.data === void 0 ? !0 : e === "static" ? !1 : this.state.isInvalidated ? !0 : !Us(this.state.dataUpdatedAt, e);
  }
  onFocus() {
    this.observers.find((t) => t.shouldFetchOnWindowFocus())?.refetch({ cancelRefetch: !1 }), this.#a?.continue();
  }
  onOnline() {
    this.observers.find((t) => t.shouldFetchOnReconnect())?.refetch({ cancelRefetch: !1 }), this.#a?.continue();
  }
  addObserver(e) {
    this.observers.includes(e) || (this.observers.push(e), this.clearGcTimeout(), this.#t.notify({ type: "observerAdded", query: this, observer: e }));
  }
  removeObserver(e) {
    this.observers.includes(e) && (this.observers = this.observers.filter((t) => t !== e), this.observers.length || (this.#a && (this.#o ? this.#a.cancel({ revert: !0 }) : this.#a.cancelRetry()), this.scheduleGc()), this.#t.notify({ type: "observerRemoved", query: this, observer: e }));
  }
  getObserversCount() {
    return this.observers.length;
  }
  invalidate() {
    this.state.isInvalidated || this.#i({ type: "invalidate" });
  }
  async fetch(e, t) {
    if (this.state.fetchStatus !== "idle" && // If the promise in the retryer is already rejected, we have to definitely
    // re-start the fetch; there is a chance that the query is still in a
    // pending state when that happens
    this.#a?.status() !== "rejected") {
      if (this.state.data !== void 0 && t?.cancelRefetch)
        this.cancel({ silent: !0 });
      else if (this.#a)
        return this.#a.continueRetry(), this.#a.promise;
    }
    if (e && this.setOptions(e), !this.options.queryFn) {
      const o = this.observers.find((l) => l.options.queryFn);
      o && this.setOptions(o.options);
    }
    process.env.NODE_ENV !== "production" && (Array.isArray(this.options.queryKey) || console.error(
      "As of v4, queryKey needs to be an Array. If you are using a string like 'repoData', please change it to an Array, e.g. ['repoData']"
    ));
    const n = new AbortController(), r = (o) => {
      Object.defineProperty(o, "signal", {
        enumerable: !0,
        get: () => (this.#o = !0, n.signal)
      });
    }, a = () => {
      const o = Er(this.options, t), c = (() => {
        const u = {
          client: this.#r,
          queryKey: this.queryKey,
          meta: this.meta
        };
        return r(u), u;
      })();
      return this.#o = !1, this.options.persister ? this.options.persister(
        o,
        c,
        this
      ) : o(c);
    }, s = (() => {
      const o = {
        fetchOptions: t,
        options: this.options,
        queryKey: this.queryKey,
        client: this.#r,
        state: this.state,
        fetchFn: a
      };
      return r(o), o;
    })();
    this.options.behavior?.onFetch(s, this), this.#n = this.state, (this.state.fetchStatus === "idle" || this.state.fetchMeta !== s.fetchOptions?.meta) && this.#i({ type: "fetch", meta: s.fetchOptions?.meta }), this.#a = Cr({
      initialPromise: t?.initialPromise,
      fn: s.fetchFn,
      onCancel: (o) => {
        o instanceof In && o.revert && this.setState({
          ...this.#n,
          fetchStatus: "idle"
        }), n.abort();
      },
      onFail: (o, l) => {
        this.#i({ type: "failed", failureCount: o, error: l });
      },
      onPause: () => {
        this.#i({ type: "pause" });
      },
      onContinue: () => {
        this.#i({ type: "continue" });
      },
      retry: s.options.retry,
      retryDelay: s.options.retryDelay,
      networkMode: s.options.networkMode,
      canRun: () => !0
    });
    try {
      const o = await this.#a.start();
      if (o === void 0)
        throw process.env.NODE_ENV !== "production" && console.error(
          `Query data cannot be undefined. Please make sure to return a value other than undefined from your query function. Affected query key: ${this.queryHash}`
        ), new Error(`${this.queryHash} data is undefined`);
      return this.setData(o), this.#t.config.onSuccess?.(o, this), this.#t.config.onSettled?.(
        o,
        this.state.error,
        this
      ), o;
    } catch (o) {
      if (o instanceof In) {
        if (o.silent)
          return this.#a.promise;
        if (o.revert) {
          if (this.state.data === void 0)
            throw o;
          return this.state.data;
        }
      }
      throw this.#i({
        type: "error",
        error: o
      }), this.#t.config.onError?.(
        o,
        this
      ), this.#t.config.onSettled?.(
        this.state.data,
        o,
        this
      ), o;
    } finally {
      this.scheduleGc();
    }
  }
  #i(e) {
    const t = (n) => {
      switch (e.type) {
        case "failed":
          return {
            ...n,
            fetchFailureCount: e.failureCount,
            fetchFailureReason: e.error
          };
        case "pause":
          return {
            ...n,
            fetchStatus: "paused"
          };
        case "continue":
          return {
            ...n,
            fetchStatus: "fetching"
          };
        case "fetch":
          return {
            ...n,
            ...so(n.data, this.options),
            fetchMeta: e.meta ?? null
          };
        case "success":
          const r = {
            ...n,
            ...ga(e.data, e.dataUpdatedAt),
            dataUpdateCount: n.dataUpdateCount + 1,
            ...!e.manual && {
              fetchStatus: "idle",
              fetchFailureCount: 0,
              fetchFailureReason: null
            }
          };
          return this.#n = e.manual ? r : void 0, r;
        case "error":
          const a = e.error;
          return {
            ...n,
            error: a,
            errorUpdateCount: n.errorUpdateCount + 1,
            errorUpdatedAt: Date.now(),
            fetchFailureCount: n.fetchFailureCount + 1,
            fetchFailureReason: a,
            fetchStatus: "idle",
            status: "error",
            // flag existing data as invalidated if we get a background error
            // note that "no data" always means stale so we can set unconditionally here
            isInvalidated: !0
          };
        case "invalidate":
          return {
            ...n,
            isInvalidated: !0
          };
        case "setState":
          return {
            ...n,
            ...e.state
          };
      }
    };
    this.state = t(this.state), ue.batch(() => {
      this.observers.forEach((n) => {
        n.onQueryUpdate();
      }), this.#t.notify({ query: this, type: "updated", action: e });
    });
  }
};
function so(e, t) {
  return {
    fetchFailureCount: 0,
    fetchFailureReason: null,
    fetchStatus: Ir(t.networkMode) ? "fetching" : "paused",
    ...e === void 0 && {
      error: null,
      status: "pending"
    }
  };
}
function ga(e, t) {
  return {
    data: e,
    dataUpdatedAt: t ?? Date.now(),
    error: null,
    isInvalidated: !1,
    status: "success"
  };
}
function ya(e) {
  const t = typeof e.initialData == "function" ? e.initialData() : e.initialData, n = t !== void 0, r = n ? typeof e.initialDataUpdatedAt == "function" ? e.initialDataUpdatedAt() : e.initialDataUpdatedAt : 0;
  return {
    data: t,
    dataUpdateCount: 0,
    dataUpdatedAt: n ? r ?? Date.now() : 0,
    error: null,
    errorUpdateCount: 0,
    errorUpdatedAt: 0,
    fetchFailureCount: 0,
    fetchFailureReason: null,
    fetchMeta: null,
    isInvalidated: !1,
    status: n ? "success" : "pending",
    fetchStatus: "idle"
  };
}
function ba(e) {
  return {
    onFetch: (t, n) => {
      const r = t.options, a = t.fetchOptions?.meta?.fetchMore?.direction, i = t.state.data?.pages || [], s = t.state.data?.pageParams || [];
      let o = { pages: [], pageParams: [] }, l = 0;
      const c = async () => {
        let u = !1;
        const d = (h) => {
          Ys(
            h,
            () => t.signal,
            () => u = !0
          );
        }, p = Er(t.options, t.fetchOptions), m = async (h, g, v) => {
          if (u)
            return Promise.reject();
          if (g == null && h.pages.length)
            return Promise.resolve(h);
          const f = (() => {
            const b = {
              client: t.client,
              queryKey: t.queryKey,
              pageParam: g,
              direction: v ? "backward" : "forward",
              meta: t.options.meta
            };
            return d(b), b;
          })(), w = await p(f), { maxPages: E } = t.options, x = v ? Qs : Xs;
          return {
            pages: x(h.pages, w, E),
            pageParams: x(h.pageParams, g, E)
          };
        };
        if (a && i.length) {
          const h = a === "backward", g = h ? oo : va, v = {
            pages: i,
            pageParams: s
          }, k = g(r, v);
          o = await m(v, k, h);
        } else {
          const h = e ?? i.length;
          do {
            const g = l === 0 ? s[0] ?? r.initialPageParam : va(r, o);
            if (l > 0 && g == null)
              break;
            o = await m(o, g), l++;
          } while (l < h);
        }
        return o;
      };
      t.options.persister ? t.fetchFn = () => t.options.persister?.(
        c,
        {
          client: t.client,
          queryKey: t.queryKey,
          meta: t.options.meta,
          signal: t.signal
        },
        n
      ) : t.fetchFn = c;
    }
  };
}
function va(e, { pages: t, pageParams: n }) {
  const r = t.length - 1;
  return t.length > 0 ? e.getNextPageParam(
    t[r],
    t,
    n[r],
    n
  ) : void 0;
}
function oo(e, { pages: t, pageParams: n }) {
  return t.length > 0 ? e.getPreviousPageParam?.(t[0], t, n[0], n) : void 0;
}
var lo = class extends Tr {
  #e;
  #n;
  #t;
  #r;
  constructor(e) {
    super(), this.#e = e.client, this.mutationId = e.mutationId, this.#t = e.mutationCache, this.#n = [], this.state = e.state || Sr(), this.setOptions(e.options), this.scheduleGc();
  }
  setOptions(e) {
    this.options = e, this.updateGcTime(this.options.gcTime);
  }
  get meta() {
    return this.options.meta;
  }
  addObserver(e) {
    this.#n.includes(e) || (this.#n.push(e), this.clearGcTimeout(), this.#t.notify({
      type: "observerAdded",
      mutation: this,
      observer: e
    }));
  }
  removeObserver(e) {
    this.#n = this.#n.filter((t) => t !== e), this.scheduleGc(), this.#t.notify({
      type: "observerRemoved",
      mutation: this,
      observer: e
    });
  }
  optionalRemove() {
    this.#n.length || (this.state.status === "pending" ? this.scheduleGc() : this.#t.remove(this));
  }
  continue() {
    return this.#r?.continue() ?? // continuing a mutation assumes that variables are set, mutation must have been dehydrated before
    this.execute(this.state.variables);
  }
  async execute(e) {
    const t = () => {
      this.#a({ type: "continue" });
    }, n = {
      client: this.#e,
      meta: this.options.meta,
      mutationKey: this.options.mutationKey
    };
    this.#r = Cr({
      fn: () => this.options.mutationFn ? this.options.mutationFn(e, n) : Promise.reject(new Error("No mutationFn found")),
      onFail: (i, s) => {
        this.#a({ type: "failed", failureCount: i, error: s });
      },
      onPause: () => {
        this.#a({ type: "pause" });
      },
      onContinue: t,
      retry: this.options.retry ?? 0,
      retryDelay: this.options.retryDelay,
      networkMode: this.options.networkMode,
      canRun: () => this.#t.canRun(this)
    });
    const r = this.state.status === "pending", a = !this.#r.canStart();
    try {
      if (r)
        t();
      else {
        this.#a({ type: "pending", variables: e, isPaused: a }), this.#t.config.onMutate && await this.#t.config.onMutate(
          e,
          this,
          n
        );
        const s = await this.options.onMutate?.(
          e,
          n
        );
        s !== this.state.context && this.#a({
          type: "pending",
          context: s,
          variables: e,
          isPaused: a
        });
      }
      const i = await this.#r.start();
      return await this.#t.config.onSuccess?.(
        i,
        e,
        this.state.context,
        this,
        n
      ), await this.options.onSuccess?.(
        i,
        e,
        this.state.context,
        n
      ), await this.#t.config.onSettled?.(
        i,
        null,
        this.state.variables,
        this.state.context,
        this,
        n
      ), await this.options.onSettled?.(
        i,
        null,
        e,
        this.state.context,
        n
      ), this.#a({ type: "success", data: i }), i;
    } catch (i) {
      try {
        await this.#t.config.onError?.(
          i,
          e,
          this.state.context,
          this,
          n
        );
      } catch (s) {
        Promise.reject(s);
      }
      try {
        await this.options.onError?.(
          i,
          e,
          this.state.context,
          n
        );
      } catch (s) {
        Promise.reject(s);
      }
      try {
        await this.#t.config.onSettled?.(
          void 0,
          i,
          this.state.variables,
          this.state.context,
          this,
          n
        );
      } catch (s) {
        Promise.reject(s);
      }
      try {
        await this.options.onSettled?.(
          void 0,
          i,
          e,
          this.state.context,
          n
        );
      } catch (s) {
        Promise.reject(s);
      }
      throw this.#a({ type: "error", error: i }), i;
    } finally {
      this.#t.runNext(this);
    }
  }
  #a(e) {
    const t = (n) => {
      switch (e.type) {
        case "failed":
          return {
            ...n,
            failureCount: e.failureCount,
            failureReason: e.error
          };
        case "pause":
          return {
            ...n,
            isPaused: !0
          };
        case "continue":
          return {
            ...n,
            isPaused: !1
          };
        case "pending":
          return {
            ...n,
            context: e.context,
            data: void 0,
            failureCount: 0,
            failureReason: null,
            error: null,
            isPaused: e.isPaused,
            status: "pending",
            variables: e.variables,
            submittedAt: Date.now()
          };
        case "success":
          return {
            ...n,
            data: e.data,
            failureCount: 0,
            failureReason: null,
            error: null,
            status: "success",
            isPaused: !1
          };
        case "error":
          return {
            ...n,
            data: void 0,
            error: e.error,
            failureCount: n.failureCount + 1,
            failureReason: e.error,
            isPaused: !1,
            status: "error"
          };
      }
    };
    this.state = t(this.state), ue.batch(() => {
      this.#n.forEach((n) => {
        n.onMutationUpdate(e);
      }), this.#t.notify({
        mutation: this,
        type: "updated",
        action: e
      });
    });
  }
};
function Sr() {
  return {
    context: void 0,
    data: void 0,
    error: null,
    failureCount: 0,
    failureReason: null,
    isPaused: !1,
    status: "idle",
    variables: void 0,
    submittedAt: 0
  };
}
var co = class extends Bt {
  constructor(e = {}) {
    super(), this.config = e, this.#e = /* @__PURE__ */ new Set(), this.#n = /* @__PURE__ */ new Map(), this.#t = 0;
  }
  #e;
  #n;
  #t;
  build(e, t, n) {
    const r = new lo({
      client: e,
      mutationCache: this,
      mutationId: ++this.#t,
      options: e.defaultMutationOptions(t),
      state: n
    });
    return this.add(r), r;
  }
  add(e) {
    this.#e.add(e);
    const t = Gt(e);
    if (typeof t == "string") {
      const n = this.#n.get(t);
      n ? n.push(e) : this.#n.set(t, [e]);
    }
    this.notify({ type: "added", mutation: e });
  }
  remove(e) {
    if (this.#e.delete(e)) {
      const t = Gt(e);
      if (typeof t == "string") {
        const n = this.#n.get(t);
        if (n)
          if (n.length > 1) {
            const r = n.indexOf(e);
            r !== -1 && n.splice(r, 1);
          } else n[0] === e && this.#n.delete(t);
      }
    }
    this.notify({ type: "removed", mutation: e });
  }
  canRun(e) {
    const t = Gt(e);
    if (typeof t == "string") {
      const r = this.#n.get(t)?.find(
        (a) => a.state.status === "pending"
      );
      return !r || r === e;
    } else
      return !0;
  }
  runNext(e) {
    const t = Gt(e);
    return typeof t == "string" ? this.#n.get(t)?.find((r) => r !== e && r.state.isPaused)?.continue() ?? Promise.resolve() : Promise.resolve();
  }
  clear() {
    ue.batch(() => {
      this.#e.forEach((e) => {
        this.notify({ type: "removed", mutation: e });
      }), this.#e.clear(), this.#n.clear();
    });
  }
  getAll() {
    return Array.from(this.#e);
  }
  find(e) {
    const t = { exact: !0, ...e };
    return this.getAll().find(
      (n) => ma(t, n)
    );
  }
  findAll(e = {}) {
    return this.getAll().filter((t) => ma(e, t));
  }
  notify(e) {
    ue.batch(() => {
      this.listeners.forEach((t) => {
        t(e);
      });
    });
  }
  resumePausedMutations() {
    const e = this.getAll().filter((t) => t.state.isPaused);
    return ue.batch(
      () => Promise.all(
        e.map((t) => t.continue().catch(Ae))
      )
    );
  }
};
function Gt(e) {
  return e.options.scope?.id;
}
var uo = class extends Bt {
  #e;
  #n = void 0;
  #t;
  #r;
  constructor(e, t) {
    super(), this.#e = e, this.setOptions(t), this.bindMethods(), this.#a();
  }
  bindMethods() {
    this.mutate = this.mutate.bind(this), this.reset = this.reset.bind(this);
  }
  setOptions(e) {
    const t = this.options;
    this.options = this.#e.defaultMutationOptions(e), Vs(this.options, t) || this.#e.getMutationCache().notify({
      type: "observerOptionsUpdated",
      mutation: this.#t,
      observer: this
    }), t?.mutationKey && this.options.mutationKey && ut(t.mutationKey) !== ut(this.options.mutationKey) ? this.reset() : this.#t?.state.status === "pending" && this.#t.setOptions(this.options);
  }
  onUnsubscribe() {
    this.hasListeners() || this.#t?.removeObserver(this);
  }
  onMutationUpdate(e) {
    this.#a(), this.#s(e);
  }
  getCurrentResult() {
    return this.#n;
  }
  reset() {
    this.#t?.removeObserver(this), this.#t = void 0, this.#a(), this.#s();
  }
  mutate(e, t) {
    return this.#r = t, this.#t?.removeObserver(this), this.#t = this.#e.getMutationCache().build(this.#e, this.options), this.#t.addObserver(this), this.#t.execute(e);
  }
  #a() {
    const e = this.#t?.state ?? Sr();
    this.#n = {
      ...e,
      isPending: e.status === "pending",
      isSuccess: e.status === "success",
      isError: e.status === "error",
      isIdle: e.status === "idle",
      mutate: this.mutate,
      reset: this.reset
    };
  }
  #s(e) {
    ue.batch(() => {
      if (this.#r && this.hasListeners()) {
        const t = this.#n.variables, n = this.#n.context, r = {
          client: this.#e,
          meta: this.options.meta,
          mutationKey: this.options.mutationKey
        };
        if (e?.type === "success") {
          try {
            this.#r.onSuccess?.(
              e.data,
              t,
              n,
              r
            );
          } catch (a) {
            Promise.reject(a);
          }
          try {
            this.#r.onSettled?.(
              e.data,
              null,
              t,
              n,
              r
            );
          } catch (a) {
            Promise.reject(a);
          }
        } else if (e?.type === "error") {
          try {
            this.#r.onError?.(
              e.error,
              t,
              n,
              r
            );
          } catch (a) {
            Promise.reject(a);
          }
          try {
            this.#r.onSettled?.(
              void 0,
              e.error,
              t,
              n,
              r
            );
          } catch (a) {
            Promise.reject(a);
          }
        }
      }
      this.listeners.forEach((t) => {
        t(this.#n);
      });
    });
  }
}, po = class extends Bt {
  constructor(e = {}) {
    super(), this.config = e, this.#e = /* @__PURE__ */ new Map();
  }
  #e;
  build(e, t, n) {
    const r = t.queryKey, a = t.queryHash ?? Fn(r, t);
    let i = this.get(a);
    return i || (i = new io({
      client: e,
      queryKey: r,
      queryHash: a,
      options: e.defaultQueryOptions(t),
      state: n,
      defaultOptions: e.getQueryDefaults(r)
    }), this.add(i)), i;
  }
  add(e) {
    this.#e.has(e.queryHash) || (this.#e.set(e.queryHash, e), this.notify({
      type: "added",
      query: e
    }));
  }
  remove(e) {
    const t = this.#e.get(e.queryHash);
    t && (e.destroy(), t === e && this.#e.delete(e.queryHash), this.notify({ type: "removed", query: e }));
  }
  clear() {
    ue.batch(() => {
      this.getAll().forEach((e) => {
        this.remove(e);
      });
    });
  }
  get(e) {
    return this.#e.get(e);
  }
  getAll() {
    return [...this.#e.values()];
  }
  find(e) {
    const t = { exact: !0, ...e };
    return this.getAll().find(
      (n) => pa(t, n)
    );
  }
  findAll(e = {}) {
    const t = this.getAll();
    return Object.keys(e).length > 0 ? t.filter((n) => pa(e, n)) : t;
  }
  notify(e) {
    ue.batch(() => {
      this.listeners.forEach((t) => {
        t(e);
      });
    });
  }
  onFocus() {
    ue.batch(() => {
      this.getAll().forEach((e) => {
        e.onFocus();
      });
    });
  }
  onOnline() {
    ue.batch(() => {
      this.getAll().forEach((e) => {
        e.onOnline();
      });
    });
  }
}, mo = class {
  #e;
  #n;
  #t;
  #r;
  #a;
  #s;
  #o;
  #i;
  constructor(e = {}) {
    this.#e = e.queryCache || new po(), this.#n = e.mutationCache || new co(), this.#t = e.defaultOptions || {}, this.#r = /* @__PURE__ */ new Map(), this.#a = /* @__PURE__ */ new Map(), this.#s = 0;
  }
  mount() {
    this.#s++, this.#s === 1 && (this.#o = _r.subscribe(async (e) => {
      e && (await this.resumePausedMutations(), this.#e.onFocus());
    }), this.#i = en.subscribe(async (e) => {
      e && (await this.resumePausedMutations(), this.#e.onOnline());
    }));
  }
  unmount() {
    this.#s--, this.#s === 0 && (this.#o?.(), this.#o = void 0, this.#i?.(), this.#i = void 0);
  }
  isFetching(e) {
    return this.#e.findAll({ ...e, fetchStatus: "fetching" }).length;
  }
  isMutating(e) {
    return this.#n.findAll({ ...e, status: "pending" }).length;
  }
  /**
   * Imperative (non-reactive) way to retrieve data for a QueryKey.
   * Should only be used in callbacks or functions where reading the latest data is necessary, e.g. for optimistic updates.
   *
   * Hint: Do not use this function inside a component, because it won't receive updates.
   * Use `useQuery` to create a `QueryObserver` that subscribes to changes.
   */
  getQueryData(e) {
    const t = this.defaultQueryOptions({ queryKey: e });
    return this.#e.get(t.queryHash)?.state.data;
  }
  ensureQueryData(e) {
    const t = this.defaultQueryOptions(e), n = this.#e.build(this, t), r = n.state.data;
    return r === void 0 ? this.fetchQuery(e) : (e.revalidateIfStale && n.isStaleByTime(xn(t.staleTime, n)) && this.prefetchQuery(t), Promise.resolve(r));
  }
  getQueriesData(e) {
    return this.#e.findAll(e).map(({ queryKey: t, state: n }) => {
      const r = n.data;
      return [t, r];
    });
  }
  setQueryData(e, t, n) {
    const r = this.defaultQueryOptions({ queryKey: e }), i = this.#e.get(
      r.queryHash
    )?.state.data, s = qs(t, i);
    if (s !== void 0)
      return this.#e.build(this, r).setData(s, { ...n, manual: !0 });
  }
  setQueriesData(e, t, n) {
    return ue.batch(
      () => this.#e.findAll(e).map(({ queryKey: r }) => [
        r,
        this.setQueryData(r, t, n)
      ])
    );
  }
  getQueryState(e) {
    const t = this.defaultQueryOptions({ queryKey: e });
    return this.#e.get(
      t.queryHash
    )?.state;
  }
  removeQueries(e) {
    const t = this.#e;
    ue.batch(() => {
      t.findAll(e).forEach((n) => {
        t.remove(n);
      });
    });
  }
  resetQueries(e, t) {
    const n = this.#e;
    return ue.batch(() => (n.findAll(e).forEach((r) => {
      r.reset();
    }), this.refetchQueries(
      {
        type: "active",
        ...e
      },
      t
    )));
  }
  cancelQueries(e, t = {}) {
    const n = { revert: !0, ...t }, r = ue.batch(
      () => this.#e.findAll(e).map((a) => a.cancel(n))
    );
    return Promise.all(r).then(Ae).catch(Ae);
  }
  invalidateQueries(e, t = {}) {
    return ue.batch(() => (this.#e.findAll(e).forEach((n) => {
      n.invalidate();
    }), e?.refetchType === "none" ? Promise.resolve() : this.refetchQueries(
      {
        ...e,
        type: e?.refetchType ?? e?.type ?? "active"
      },
      t
    )));
  }
  refetchQueries(e, t = {}) {
    const n = {
      ...t,
      cancelRefetch: t.cancelRefetch ?? !0
    }, r = ue.batch(
      () => this.#e.findAll(e).filter((a) => !a.isDisabled() && !a.isStatic()).map((a) => {
        let i = a.fetch(void 0, n);
        return n.throwOnError || (i = i.catch(Ae)), a.state.fetchStatus === "paused" ? Promise.resolve() : i;
      })
    );
    return Promise.all(r).then(Ae);
  }
  fetchQuery(e) {
    const t = this.defaultQueryOptions(e);
    t.retry === void 0 && (t.retry = !1);
    const n = this.#e.build(this, t);
    return n.isStaleByTime(
      xn(t.staleTime, n)
    ) ? n.fetch(t) : Promise.resolve(n.state.data);
  }
  prefetchQuery(e) {
    return this.fetchQuery(e).then(Ae).catch(Ae);
  }
  fetchInfiniteQuery(e) {
    return e.behavior = ba(e.pages), this.fetchQuery(e);
  }
  prefetchInfiniteQuery(e) {
    return this.fetchInfiniteQuery(e).then(Ae).catch(Ae);
  }
  ensureInfiniteQueryData(e) {
    return e.behavior = ba(e.pages), this.ensureQueryData(e);
  }
  resumePausedMutations() {
    return en.isOnline() ? this.#n.resumePausedMutations() : Promise.resolve();
  }
  getQueryCache() {
    return this.#e;
  }
  getMutationCache() {
    return this.#n;
  }
  getDefaultOptions() {
    return this.#t;
  }
  setDefaultOptions(e) {
    this.#t = e;
  }
  setQueryDefaults(e, t) {
    this.#r.set(ut(e), {
      queryKey: e,
      defaultOptions: t
    });
  }
  getQueryDefaults(e) {
    const t = [...this.#r.values()], n = {};
    return t.forEach((r) => {
      jt(e, r.queryKey) && Object.assign(n, r.defaultOptions);
    }), n;
  }
  setMutationDefaults(e, t) {
    this.#a.set(ut(e), {
      mutationKey: e,
      defaultOptions: t
    });
  }
  getMutationDefaults(e) {
    const t = [...this.#a.values()], n = {};
    return t.forEach((r) => {
      jt(e, r.mutationKey) && Object.assign(n, r.defaultOptions);
    }), n;
  }
  defaultQueryOptions(e) {
    if (e._defaulted)
      return e;
    const t = {
      ...this.#t.queries,
      ...this.getQueryDefaults(e.queryKey),
      ...e,
      _defaulted: !0
    };
    return t.queryHash || (t.queryHash = Fn(
      t.queryKey,
      t
    )), t.refetchOnReconnect === void 0 && (t.refetchOnReconnect = t.networkMode !== "always"), t.throwOnError === void 0 && (t.throwOnError = !!t.suspense), !t.networkMode && t.persister && (t.networkMode = "offlineFirst"), t.queryFn === Zt && (t.enabled = !1), t;
  }
  defaultMutationOptions(e) {
    return e?._defaulted ? e : {
      ...this.#t.mutations,
      ...e?.mutationKey && this.getMutationDefaults(e.mutationKey),
      ...e,
      _defaulted: !0
    };
  }
  clear() {
    this.#e.clear(), this.#n.clear();
  }
}, Rr = W.createContext(
  void 0
), ho = (e) => {
  const t = W.useContext(Rr);
  if (!t)
    throw new Error("No QueryClient set, use QueryClientProvider to set one");
  return t;
}, fo = ({
  client: e,
  children: t
}) => (W.useEffect(() => (e.mount(), () => {
  e.unmount();
}), [e]), /* @__PURE__ */ y(Rr.Provider, { value: e, children: t }));
function go(e, t) {
  const n = ho(), [r] = W.useState(
    () => new uo(
      n,
      e
    )
  );
  W.useEffect(() => {
    r.setOptions(e);
  }, [r, e]);
  const a = W.useSyncExternalStore(
    W.useCallback(
      (s) => r.subscribe(ue.batchCalls(s)),
      [r]
    ),
    () => r.getCurrentResult(),
    () => r.getCurrentResult()
  ), i = W.useCallback(
    (s, o) => {
      r.mutate(s, o).catch(Ae);
    },
    [r]
  );
  if (a.error && Js(r.options.throwOnError, [a.error]))
    throw a.error;
  return { ...a, mutate: i, mutateAsync: a.mutate };
}
function Ht(e) {
  for (var t = 1; t < arguments.length; t++) {
    var n = arguments[t];
    for (var r in n)
      e[r] = n[r];
  }
  return e;
}
var yo = {
  read: function(e) {
    return e[0] === '"' && (e = e.slice(1, -1)), e.replace(/(%[\dA-F]{2})+/gi, decodeURIComponent);
  },
  write: function(e) {
    return encodeURIComponent(e).replace(
      /%(2[346BF]|3[AC-F]|40|5[BDE]|60|7[BCD])/g,
      decodeURIComponent
    );
  }
};
function Cn(e, t) {
  function n(a, i, s) {
    if (!(typeof document > "u")) {
      s = Ht({}, t, s), typeof s.expires == "number" && (s.expires = new Date(Date.now() + s.expires * 864e5)), s.expires && (s.expires = s.expires.toUTCString()), a = encodeURIComponent(a).replace(/%(2[346B]|5E|60|7C)/g, decodeURIComponent).replace(/[()]/g, escape);
      var o = "";
      for (var l in s)
        s[l] && (o += "; " + l, s[l] !== !0 && (o += "=" + s[l].split(";")[0]));
      return document.cookie = a + "=" + e.write(i, a) + o;
    }
  }
  function r(a) {
    if (!(typeof document > "u" || arguments.length && !a)) {
      for (var i = document.cookie ? document.cookie.split("; ") : [], s = {}, o = 0; o < i.length; o++) {
        var l = i[o].split("="), c = l.slice(1).join("=");
        try {
          var u = decodeURIComponent(l[0]);
          if (s[u] = e.read(c, u), a === u)
            break;
        } catch {
        }
      }
      return a ? s[a] : s;
    }
  }
  return Object.create(
    {
      set: n,
      get: r,
      remove: function(a, i) {
        n(
          a,
          "",
          Ht({}, i, {
            expires: -1
          })
        );
      },
      withAttributes: function(a) {
        return Cn(this.converter, Ht({}, this.attributes, a));
      },
      withConverter: function(a) {
        return Cn(Ht({}, this.converter, a), this.attributes);
      }
    },
    {
      attributes: { value: Object.freeze(t) },
      converter: { value: Object.freeze(e) }
    }
  );
}
var ka = Cn(yo, { path: "/" });
function bo(e, t) {
  return e.endsWith(t) ? e.length === t.length || e[e.length - t.length - 1] === "." : !1;
}
function vo(e, t) {
  const n = e.length - t.length - 2, r = e.lastIndexOf(".", n);
  return r === -1 ? e : e.slice(r + 1);
}
function ko(e, t, n) {
  if (n.validHosts !== null) {
    const a = n.validHosts;
    for (const i of a)
      if (
        /*@__INLINE__*/
        bo(t, i)
      )
        return i;
  }
  let r = 0;
  if (t.startsWith("."))
    for (; r < t.length && t[r] === "."; )
      r += 1;
  return e.length === t.length - r ? null : (
    /*@__INLINE__*/
    vo(t, e)
  );
}
function wa(e, t) {
  let n = 0, r = e.length, a = !1;
  if (!t) {
    if (e.startsWith("data:"))
      return null;
    for (; n < e.length && e.charCodeAt(n) <= 32; )
      n += 1;
    for (; r > n + 1 && e.charCodeAt(r - 1) <= 32; )
      r -= 1;
    if (e.charCodeAt(n) === 47 && e.charCodeAt(n + 1) === 47)
      n += 2;
    else {
      const c = e.indexOf(":/", n);
      if (c !== -1) {
        const u = c - n, d = e.charCodeAt(n), p = e.charCodeAt(n + 1), m = e.charCodeAt(n + 2), h = e.charCodeAt(n + 3), g = e.charCodeAt(n + 4);
        if (!(u === 5 && d === 104 && p === 116 && m === 116 && h === 112 && g === 115)) {
          if (!(u === 4 && d === 104 && p === 116 && m === 116 && h === 112)) {
            if (!(u === 3 && d === 119 && p === 115 && m === 115)) {
              if (!(u === 2 && d === 119 && p === 115)) for (let v = n; v < c; v += 1) {
                const k = e.charCodeAt(v) | 32;
                if (!(k >= 97 && k <= 122 || // [a, z]
                k >= 48 && k <= 57 || // [0, 9]
                k === 46 || // '.'
                k === 45 || // '-'
                k === 43))
                  return null;
              }
            }
          }
        }
        for (n = c + 2; e.charCodeAt(n) === 47; )
          n += 1;
      }
    }
    let s = -1, o = -1, l = -1;
    for (let c = n; c < r; c += 1) {
      const u = e.charCodeAt(c);
      if (u === 35 || // '#'
      u === 47 || // '/'
      u === 63) {
        r = c;
        break;
      } else u === 64 ? s = c : u === 93 ? o = c : u === 58 ? l = c : u >= 65 && u <= 90 && (a = !0);
    }
    if (s !== -1 && s > n && s < r && (n = s + 1), e.charCodeAt(n) === 91)
      return o !== -1 ? e.slice(n + 1, o).toLowerCase() : null;
    l !== -1 && l > n && l < r && (r = l);
  }
  for (; r > n + 1 && e.charCodeAt(r - 1) === 46; )
    r -= 1;
  const i = n !== 0 || r !== e.length ? e.slice(n, r) : e;
  return a ? i.toLowerCase() : i;
}
function wo(e) {
  if (e.length < 7 || e.length > 15)
    return !1;
  let t = 0;
  for (let n = 0; n < e.length; n += 1) {
    const r = e.charCodeAt(n);
    if (r === 46)
      t += 1;
    else if (r < 48 || r > 57)
      return !1;
  }
  return t === 3 && e.charCodeAt(0) !== 46 && e.charCodeAt(e.length - 1) !== 46;
}
function xo(e) {
  if (e.length < 3)
    return !1;
  let t = e.startsWith("[") ? 1 : 0, n = e.length;
  if (e[n - 1] === "]" && (n -= 1), n - t > 39)
    return !1;
  let r = !1;
  for (; t < n; t += 1) {
    const a = e.charCodeAt(t);
    if (a === 58)
      r = !0;
    else if (!(a >= 48 && a <= 57 || // 0-9
    a >= 97 && a <= 102 || // a-f
    a >= 65 && a <= 90))
      return !1;
  }
  return r;
}
function Eo(e) {
  return xo(e) || wo(e);
}
function xa(e) {
  return e >= 97 && e <= 122 || e >= 48 && e <= 57 || e > 127;
}
function Ea(e) {
  if (e.length > 255 || e.length === 0 || /*@__INLINE__*/
  !xa(e.charCodeAt(0)) && e.charCodeAt(0) !== 46 && // '.' (dot)
  e.charCodeAt(0) !== 95)
    return !1;
  let t = -1, n = -1;
  const r = e.length;
  for (let a = 0; a < r; a += 1) {
    const i = e.charCodeAt(a);
    if (i === 46) {
      if (
        // Check that previous label is < 63 bytes long (64 = 63 + '.')
        a - t > 64 || // Check that previous character was not already a '.'
        n === 46 || // Check that the previous label does not end with a '-' (dash)
        n === 45 || // Check that the previous label does not end with a '_' (underscore)
        n === 95
      )
        return !1;
      t = a;
    } else if (!/*@__INLINE__*/
    (xa(i) || i === 45 || i === 95))
      return !1;
    n = i;
  }
  return (
    // Check that last label is shorter than 63 chars
    r - t - 1 <= 63 && // Check that the last character is an allowed trailing label character.
    // Since we already checked that the char is a valid hostname character,
    // we only need to check that it's different from '-'.
    n !== 45
  );
}
function Nr({ allowIcannDomains: e = !0, allowPrivateDomains: t = !1, detectIp: n = !0, extractHostname: r = !0, mixedInputs: a = !0, validHosts: i = null, validateHostname: s = !0 }) {
  return {
    allowIcannDomains: e,
    allowPrivateDomains: t,
    detectIp: n,
    extractHostname: r,
    mixedInputs: a,
    validHosts: i,
    validateHostname: s
  };
}
const _o = (
  /*@__INLINE__*/
  Nr({})
);
function Io(e) {
  return e === void 0 ? _o : (
    /*@__INLINE__*/
    Nr(e)
  );
}
function Co() {
  return {
    domain: null,
    domainWithoutSuffix: null,
    hostname: null,
    isIcann: null,
    isIp: null,
    isPrivate: null,
    publicSuffix: null,
    subdomain: null
  };
}
function To(e) {
  e.domain = null, e.domainWithoutSuffix = null, e.hostname = null, e.isIcann = null, e.isIp = null, e.isPrivate = null, e.publicSuffix = null, e.subdomain = null;
}
function So(e, t, n, r, a) {
  const i = (
    /*@__INLINE__*/
    Io(r)
  );
  return typeof e != "string" || (i.extractHostname ? i.mixedInputs ? a.hostname = wa(e, Ea(e)) : a.hostname = wa(e, !1) : a.hostname = e, i.detectIp && a.hostname !== null && (a.isIp = Eo(a.hostname), a.isIp)) ? a : i.validateHostname && i.extractHostname && a.hostname !== null && !Ea(a.hostname) ? (a.hostname = null, a) : (a.hostname === null || (n(a.hostname, i, a), a.publicSuffix === null) || (a.domain = ko(a.publicSuffix, a.hostname, i)), a);
}
function Ro(e, t, n) {
  if (!t.allowPrivateDomains && e.length > 3) {
    const r = e.length - 1, a = e.charCodeAt(r), i = e.charCodeAt(r - 1), s = e.charCodeAt(r - 2), o = e.charCodeAt(r - 3);
    if (a === 109 && i === 111 && s === 99 && o === 46)
      return n.isIcann = !0, n.isPrivate = !1, n.publicSuffix = "com", !0;
    if (a === 103 && i === 114 && s === 111 && o === 46)
      return n.isIcann = !0, n.isPrivate = !1, n.publicSuffix = "org", !0;
    if (a === 117 && i === 100 && s === 101 && o === 46)
      return n.isIcann = !0, n.isPrivate = !1, n.publicSuffix = "edu", !0;
    if (a === 118 && i === 111 && s === 103 && o === 46)
      return n.isIcann = !0, n.isPrivate = !1, n.publicSuffix = "gov", !0;
    if (a === 116 && i === 101 && s === 110 && o === 46)
      return n.isIcann = !0, n.isPrivate = !1, n.publicSuffix = "net", !0;
    if (a === 101 && i === 100 && s === 46)
      return n.isIcann = !0, n.isPrivate = !1, n.publicSuffix = "de", !0;
  }
  return !1;
}
const No = /* @__PURE__ */ (function() {
  const e = [1, {}], t = [0, { city: e }];
  return [0, { ck: [0, { www: e }], jp: [0, { kawasaki: t, kitakyushu: t, kobe: t, nagoya: t, sapporo: t, sendai: t, yokohama: t }] }];
})(), Ao = /* @__PURE__ */ (function() {
  const e = [1, {}], t = [2, {}], n = [1, { com: e, edu: e, gov: e, net: e, org: e }], r = [1, { com: e, edu: e, gov: e, mil: e, net: e, org: e }], a = [0, { "*": t }], i = [2, { s: a }], s = [0, { relay: t }], o = [2, { id: t }], l = [1, { gov: e }], c = [0, { airflow: a, "lambda-url": t, "transfer-webapp": t }], u = [0, { airflow: a, "transfer-webapp": t }], d = [0, { "transfer-webapp": t }], p = [0, { "transfer-webapp": t, "transfer-webapp-fips": t }], m = [0, { notebook: t, studio: t }], h = [0, { labeling: t, notebook: t, studio: t }], g = [0, { notebook: t }], v = [0, { labeling: t, notebook: t, "notebook-fips": t, studio: t }], k = [0, { notebook: t, "notebook-fips": t, studio: t, "studio-fips": t }], f = [0, { shop: t }], w = [0, { "*": e }], E = [1, { co: t }], x = [0, { objects: t }], b = [2, { nodes: t }], I = [0, { my: t }], C = [0, { s3: t, "s3-accesspoint": t, "s3-website": t }], O = [0, { s3: t, "s3-accesspoint": t }], q = [0, { direct: t }], A = [0, { "webview-assets": t }], T = [0, { vfs: t, "webview-assets": t }], _ = [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, dualstack: C, s3: t, "s3-accesspoint": t, "s3-object-lambda": t, "s3-website": t, "aws-cloud9": A, cloud9: T }], P = [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, dualstack: O, s3: t, "s3-accesspoint": t, "s3-object-lambda": t, "s3-website": t, "aws-cloud9": A, cloud9: T }], Oe = [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, dualstack: C, s3: t, "s3-accesspoint": t, "s3-object-lambda": t, "s3-website": t, "analytics-gateway": t, "aws-cloud9": A, cloud9: T }], $ = [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, dualstack: C, s3: t, "s3-accesspoint": t, "s3-object-lambda": t, "s3-website": t }], D = [0, { s3: t, "s3-accesspoint": t, "s3-accesspoint-fips": t, "s3-fips": t, "s3-website": t }], ze = [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, dualstack: D, s3: t, "s3-accesspoint": t, "s3-accesspoint-fips": t, "s3-fips": t, "s3-object-lambda": t, "s3-website": t, "aws-cloud9": A, cloud9: T }], Pe = [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, dualstack: D, s3: t, "s3-accesspoint": t, "s3-accesspoint-fips": t, "s3-fips": t, "s3-object-lambda": t, "s3-website": t }], ne = [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, dualstack: D, s3: t, "s3-accesspoint": t, "s3-accesspoint-fips": t, "s3-deprecated": t, "s3-fips": t, "s3-object-lambda": t, "s3-website": t, "analytics-gateway": t, "aws-cloud9": A, cloud9: T }], B = [0, { auth: t }], Le = [0, { auth: t, "auth-fips": t }], et = [0, { "auth-fips": t }], tt = [0, { apps: t }], Ie = [0, { paas: t }], S = [2, { eu: t }], Rt = [0, { app: t }], Ce = [0, { site: t }], Te = [1, { com: e, edu: e, net: e, org: e }], fe = [0, { j: t }], Fe = [0, { dyn: t }], Se = [2, { web: t }], K = [1, { discourse: t }], ae = [1, { co: e, com: e, edu: e, gov: e, net: e, org: e }], qe = [0, { p: t }], Nt = [0, { user: t }], ge = [0, { cdn: t }], Re = [2, { raw: a }], ye = [0, { cust: t, reservd: t }], U = [0, { cust: t }], $e = [0, { s3: t }], At = [1, { biz: e, com: e, edu: e, gov: e, info: e, net: e, org: e }], he = [0, { ipfs: t }], R = [1, { framer: t }], z = [0, { forgot: t }], Ii = [0, { blob: t, file: t, web: t }], ta = [0, { core: Ii, servicebus: t }], Z = [1, { gs: e }], na = [0, { nes: e }], N = [1, { k12: e, cc: e, lib: e }], aa = [1, { cc: e }], qt = [1, { cc: e, lib: e }];
  return [0, { ac: [1, { com: e, edu: e, gov: e, mil: e, net: e, org: e, drr: t, feedback: t, forms: t }], ad: e, ae: [1, { ac: e, co: e, gov: e, mil: e, net: e, org: e, sch: e }], aero: [1, { airline: e, airport: e, "accident-investigation": e, "accident-prevention": e, aerobatic: e, aeroclub: e, aerodrome: e, agents: e, "air-surveillance": e, "air-traffic-control": e, aircraft: e, airtraffic: e, ambulance: e, association: e, author: e, ballooning: e, broker: e, caa: e, cargo: e, catering: e, certification: e, championship: e, charter: e, civilaviation: e, club: e, conference: e, consultant: e, consulting: e, control: e, council: e, crew: e, design: e, dgca: e, educator: e, emergency: e, engine: e, engineer: e, entertainment: e, equipment: e, exchange: e, express: e, federation: e, flight: e, freight: e, fuel: e, gliding: e, government: e, groundhandling: e, group: e, hanggliding: e, homebuilt: e, insurance: e, journal: e, journalist: e, leasing: e, logistics: e, magazine: e, maintenance: e, marketplace: e, media: e, microlight: e, modelling: e, navigation: e, parachuting: e, paragliding: e, "passenger-association": e, pilot: e, press: e, production: e, recreation: e, repbody: e, res: e, research: e, rotorcraft: e, safety: e, scientist: e, services: e, show: e, skydiving: e, software: e, student: e, taxi: e, trader: e, trading: e, trainer: e, union: e, workinggroup: e, works: e }], af: n, ag: [1, { co: e, com: e, net: e, nom: e, org: e, obj: t }], ai: [1, { com: e, net: e, off: e, org: e, uwu: t, framer: t, kiloapps: t }], al: r, am: [1, { co: e, com: e, commune: e, net: e, org: e, radio: t }], ao: [1, { co: e, ed: e, edu: e, gov: e, gv: e, it: e, og: e, org: e, pb: e }], aq: e, ar: [1, { bet: e, com: e, coop: e, edu: e, gob: e, gov: e, int: e, mil: e, musica: e, mutual: e, net: e, org: e, seg: e, senasa: e, tur: e }], arpa: [1, { e164: e, home: e, "in-addr": e, ip6: e, iris: e, uri: e, urn: e }], as: l, asia: [1, { cloudns: t, daemon: t, dix: t }], at: [1, { 4: t, ac: [1, { sth: e }], co: e, gv: e, or: e, funkfeuer: [0, { wien: t }], futurecms: [0, { "*": t, ex: a, in: a }], futurehosting: t, futuremailing: t, ortsinfo: [0, { ex: a, kunden: a }], biz: t, info: t, "123webseite": t, priv: t, my: t, myspreadshop: t, "12hp": t, "2ix": t, "4lima": t, "lima-city": t }], au: [1, { asn: e, com: [1, { cloudlets: [0, { mel: t }], myspreadshop: t }], edu: [1, { act: e, catholic: e, nsw: e, nt: e, qld: e, sa: e, tas: e, vic: e, wa: e }], gov: [1, { qld: e, sa: e, tas: e, vic: e, wa: e }], id: e, net: e, org: e, conf: e, oz: e, act: e, nsw: e, nt: e, qld: e, sa: e, tas: e, vic: e, wa: e, hrsn: [0, { vps: t }] }], aw: [1, { com: e }], ax: e, az: [1, { biz: e, co: e, com: e, edu: e, gov: e, info: e, int: e, mil: e, name: e, net: e, org: e, pp: e, pro: e }], ba: [1, { com: e, edu: e, gov: e, mil: e, net: e, org: e, brendly: f, rs: t }], bb: [1, { biz: e, co: e, com: e, edu: e, gov: e, info: e, net: e, org: e, store: e, tv: e }], bd: [1, { ac: e, ai: e, co: e, com: e, edu: e, gov: e, id: e, info: e, it: e, mil: e, net: e, org: e, sch: e, tv: e }], be: [1, { ac: e, cloudns: t, webhosting: t, interhostsolutions: [0, { cloud: t }], kuleuven: [0, { ezproxy: t }], "123website": t, myspreadshop: t, transurl: a }], bf: l, bg: [1, { 0: e, 1: e, 2: e, 3: e, 4: e, 5: e, 6: e, 7: e, 8: e, 9: e, a: e, b: e, c: e, d: e, e, f: e, g: e, h: e, i: e, j: e, k: e, l: e, m: e, n: e, o: e, p: e, q: e, r: e, s: e, t: e, u: e, v: e, w: e, x: e, y: e, z: e, barsy: t }], bh: n, bi: [1, { co: e, com: e, edu: e, or: e, org: e }], biz: [1, { activetrail: t, "cloud-ip": t, cloudns: t, jozi: t, dyndns: t, "for-better": t, "for-more": t, "for-some": t, "for-the": t, selfip: t, webhop: t, orx: t, mmafan: t, myftp: t, "no-ip": t, dscloud: t }], bj: [1, { africa: e, agro: e, architectes: e, assur: e, avocats: e, co: e, com: e, eco: e, econo: e, edu: e, info: e, loisirs: e, money: e, net: e, org: e, ote: e, restaurant: e, resto: e, tourism: e, univ: e }], bm: n, bn: [1, { com: e, edu: e, gov: e, net: e, org: e, co: t }], bo: [1, { com: e, edu: e, gob: e, int: e, mil: e, net: e, org: e, tv: e, web: e, academia: e, agro: e, arte: e, blog: e, bolivia: e, ciencia: e, cooperativa: e, democracia: e, deporte: e, ecologia: e, economia: e, empresa: e, indigena: e, industria: e, info: e, medicina: e, movimiento: e, musica: e, natural: e, nombre: e, noticias: e, patria: e, plurinacional: e, politica: e, profesional: e, pueblo: e, revista: e, salud: e, tecnologia: e, tksat: e, transporte: e, wiki: e }], br: [1, { "9guacu": e, abc: e, adm: e, adv: e, agr: e, aju: e, am: e, anani: e, aparecida: e, api: e, app: e, arq: e, art: e, ato: e, b: e, barueri: e, belem: e, bet: e, bhz: e, bib: e, bio: e, blog: e, bmd: e, boavista: e, bsb: e, campinagrande: e, campinas: e, caxias: e, cim: e, cng: e, cnt: e, com: [1, { simplesite: t }], contagem: e, coop: e, coz: e, cri: e, cuiaba: e, curitiba: e, def: e, des: e, det: e, dev: e, ecn: e, eco: e, edu: e, emp: e, enf: e, eng: e, esp: e, etc: e, eti: e, far: e, feira: e, flog: e, floripa: e, fm: e, fnd: e, fortal: e, fot: e, foz: e, fst: e, g12: e, geo: e, ggf: e, goiania: e, gov: [1, { ac: e, al: e, am: e, ap: e, ba: e, ce: e, df: e, es: e, go: e, ma: e, mg: e, ms: e, mt: e, pa: e, pb: e, pe: e, pi: e, pr: e, rj: e, rn: e, ro: e, rr: e, rs: e, sc: e, se: e, sp: e, to: e }], gru: e, ia: e, imb: e, ind: e, inf: e, jab: e, jampa: e, jdf: e, joinville: e, jor: e, jus: e, leg: [1, { ac: t, al: t, am: t, ap: t, ba: t, ce: t, df: t, es: t, go: t, ma: t, mg: t, ms: t, mt: t, pa: t, pb: t, pe: t, pi: t, pr: t, rj: t, rn: t, ro: t, rr: t, rs: t, sc: t, se: t, sp: t, to: t }], leilao: e, lel: e, log: e, londrina: e, macapa: e, maceio: e, manaus: e, maringa: e, mat: e, med: e, mil: e, morena: e, mp: e, mus: e, natal: e, net: e, niteroi: e, nom: w, not: e, ntr: e, odo: e, ong: e, org: e, osasco: e, palmas: e, poa: e, ppg: e, pro: e, psc: e, psi: e, pvh: e, qsl: e, radio: e, rec: e, recife: e, rep: e, ribeirao: e, rio: e, riobranco: e, riopreto: e, salvador: e, sampa: e, santamaria: e, santoandre: e, saobernardo: e, saogonca: e, seg: e, sjc: e, slg: e, slz: e, social: e, sorocaba: e, srv: e, taxi: e, tc: e, tec: e, teo: e, the: e, tmp: e, trd: e, tur: e, tv: e, udi: e, vet: e, vix: e, vlog: e, wiki: e, xyz: e, zlg: e, tche: t }], bs: [1, { com: e, edu: e, gov: e, net: e, org: e, we: t }], bt: n, bv: e, bw: [1, { ac: e, co: e, gov: e, net: e, org: e }], by: [1, { gov: e, mil: e, com: e, of: e, mediatech: t }], bz: [1, { co: e, com: e, edu: e, gov: e, net: e, org: e, za: t, mydns: t, gsj: t }], ca: [1, { ab: e, bc: e, mb: e, nb: e, nf: e, nl: e, ns: e, nt: e, nu: e, on: e, pe: e, qc: e, sk: e, yk: e, gc: e, barsy: t, awdev: a, co: t, "no-ip": t, onid: t, myspreadshop: t, box: t }], cat: e, cc: [1, { cleverapps: t, "cloud-ip": t, cloudns: t, ccwu: t, ftpaccess: t, "game-server": t, myphotos: t, scrapping: t, twmail: t, csx: t, fantasyleague: t, spawn: [0, { instances: t }], ec: t, eu: t, gu: t, uk: t, us: t }], cd: [1, { gov: e, cc: t }], cf: e, cg: e, ch: [1, { square7: t, cloudns: t, cloudscale: [0, { cust: t, lpg: x, rma: x }], objectstorage: [0, { lpg: t, rma: t }], flow: [0, { ae: [0, { alp1: t }], appengine: t }], "linkyard-cloud": t, gotdns: t, dnsking: t, "123website": t, myspreadshop: t, firenet: [0, { "*": t, svc: a }], "12hp": t, "2ix": t, "4lima": t, "lima-city": t }], ci: [1, { ac: e, "xn--aroport-bya": e, aéroport: e, asso: e, co: e, com: e, ed: e, edu: e, go: e, gouv: e, int: e, net: e, or: e, org: e, us: t }], ck: w, cl: [1, { co: e, gob: e, gov: e, mil: e, cloudns: t }], cm: [1, { co: e, com: e, gov: e, net: e }], cn: [1, { ac: e, com: [1, { amazonaws: [0, { "cn-north-1": [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, rds: a, dualstack: C, s3: t, "s3-accesspoint": t, "s3-deprecated": t, "s3-object-lambda": t, "s3-website": t }], "cn-northwest-1": [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, rds: a, dualstack: O, s3: t, "s3-accesspoint": t, "s3-object-lambda": t, "s3-website": t }], compute: a, airflow: [0, { "cn-north-1": a, "cn-northwest-1": a }], eb: [0, { "cn-north-1": t, "cn-northwest-1": t }], elb: a }], amazonwebservices: [0, { on: [0, { "cn-north-1": u, "cn-northwest-1": u }] }], sagemaker: [0, { "cn-north-1": m, "cn-northwest-1": m }] }], edu: e, gov: e, mil: e, net: e, org: e, "xn--55qx5d": e, 公司: e, "xn--od0alg": e, 網絡: e, "xn--io0a7i": e, 网络: e, ah: e, bj: e, cq: e, fj: e, gd: e, gs: e, gx: e, gz: e, ha: e, hb: e, he: e, hi: e, hk: e, hl: e, hn: e, jl: e, js: e, jx: e, ln: e, mo: e, nm: e, nx: e, qh: e, sc: e, sd: e, sh: [1, { as: t }], sn: e, sx: e, tj: e, tw: e, xj: e, xz: e, yn: e, zj: e, "canva-apps": t, canvasite: I, myqnapcloud: t, quickconnect: q }], co: [1, { com: e, edu: e, gov: e, mil: e, net: e, nom: e, org: e, carrd: t, crd: t, otap: a, hidns: t, leadpages: t, lpages: t, mypi: t, xmit: a, rdpa: [0, { clusters: a, srvrless: a }], firewalledreplit: o, repl: o, supabase: [2, { realtime: t, storage: t }], umso: t }], com: [1, { a2hosted: t, cpserver: t, adobeaemcloud: [2, { dev: a }], africa: t, auiusercontent: a, aivencloud: t, alibabacloudcs: t, kasserver: t, amazonaws: [0, { "af-south-1": _, "ap-east-1": P, "ap-northeast-1": Oe, "ap-northeast-2": Oe, "ap-northeast-3": _, "ap-south-1": Oe, "ap-south-2": $, "ap-southeast-1": Oe, "ap-southeast-2": Oe, "ap-southeast-3": $, "ap-southeast-4": $, "ap-southeast-5": [0, { "execute-api": t, dualstack: C, s3: t, "s3-accesspoint": t, "s3-deprecated": t, "s3-object-lambda": t, "s3-website": t }], "ca-central-1": ze, "ca-west-1": Pe, "eu-central-1": Oe, "eu-central-2": $, "eu-north-1": P, "eu-south-1": _, "eu-south-2": $, "eu-west-1": [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, dualstack: C, s3: t, "s3-accesspoint": t, "s3-deprecated": t, "s3-object-lambda": t, "s3-website": t, "analytics-gateway": t, "aws-cloud9": A, cloud9: T }], "eu-west-2": P, "eu-west-3": _, "il-central-1": [0, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, dualstack: C, s3: t, "s3-accesspoint": t, "s3-object-lambda": t, "s3-website": t, "aws-cloud9": A, cloud9: [0, { vfs: t }] }], "me-central-1": $, "me-south-1": P, "sa-east-1": _, "us-east-1": [2, { "execute-api": t, "emrappui-prod": t, "emrnotebooks-prod": t, "emrstudio-prod": t, dualstack: D, s3: t, "s3-accesspoint": t, "s3-accesspoint-fips": t, "s3-deprecated": t, "s3-fips": t, "s3-object-lambda": t, "s3-website": t, "analytics-gateway": t, "aws-cloud9": A, cloud9: T }], "us-east-2": ne, "us-gov-east-1": Pe, "us-gov-west-1": Pe, "us-west-1": ze, "us-west-2": ne, compute: a, "compute-1": a, airflow: [0, { "af-south-1": a, "ap-east-1": a, "ap-northeast-1": a, "ap-northeast-2": a, "ap-northeast-3": a, "ap-south-1": a, "ap-south-2": a, "ap-southeast-1": a, "ap-southeast-2": a, "ap-southeast-3": a, "ap-southeast-4": a, "ap-southeast-5": a, "ap-southeast-7": a, "ca-central-1": a, "ca-west-1": a, "eu-central-1": a, "eu-central-2": a, "eu-north-1": a, "eu-south-1": a, "eu-south-2": a, "eu-west-1": a, "eu-west-2": a, "eu-west-3": a, "il-central-1": a, "me-central-1": a, "me-south-1": a, "sa-east-1": a, "us-east-1": a, "us-east-2": a, "us-west-1": a, "us-west-2": a }], rds: [0, { "af-south-1": a, "ap-east-1": a, "ap-east-2": a, "ap-northeast-1": a, "ap-northeast-2": a, "ap-northeast-3": a, "ap-south-1": a, "ap-south-2": a, "ap-southeast-1": a, "ap-southeast-2": a, "ap-southeast-3": a, "ap-southeast-4": a, "ap-southeast-5": a, "ap-southeast-6": a, "ap-southeast-7": a, "ca-central-1": a, "ca-west-1": a, "eu-central-1": a, "eu-central-2": a, "eu-west-1": a, "eu-west-2": a, "eu-west-3": a, "il-central-1": a, "me-central-1": a, "me-south-1": a, "mx-central-1": a, "sa-east-1": a, "us-east-1": a, "us-east-2": a, "us-gov-east-1": a, "us-gov-west-1": a, "us-northeast-1": a, "us-west-1": a, "us-west-2": a }], s3: t, "s3-1": t, "s3-ap-east-1": t, "s3-ap-northeast-1": t, "s3-ap-northeast-2": t, "s3-ap-northeast-3": t, "s3-ap-south-1": t, "s3-ap-southeast-1": t, "s3-ap-southeast-2": t, "s3-ca-central-1": t, "s3-eu-central-1": t, "s3-eu-north-1": t, "s3-eu-west-1": t, "s3-eu-west-2": t, "s3-eu-west-3": t, "s3-external-1": t, "s3-fips-us-gov-east-1": t, "s3-fips-us-gov-west-1": t, "s3-global": [0, { accesspoint: [0, { mrap: t }] }], "s3-me-south-1": t, "s3-sa-east-1": t, "s3-us-east-2": t, "s3-us-gov-east-1": t, "s3-us-gov-west-1": t, "s3-us-west-1": t, "s3-us-west-2": t, "s3-website-ap-northeast-1": t, "s3-website-ap-southeast-1": t, "s3-website-ap-southeast-2": t, "s3-website-eu-west-1": t, "s3-website-sa-east-1": t, "s3-website-us-east-1": t, "s3-website-us-gov-west-1": t, "s3-website-us-west-1": t, "s3-website-us-west-2": t, elb: a }], amazoncognito: [0, { "af-south-1": B, "ap-east-1": B, "ap-northeast-1": B, "ap-northeast-2": B, "ap-northeast-3": B, "ap-south-1": B, "ap-south-2": B, "ap-southeast-1": B, "ap-southeast-2": B, "ap-southeast-3": B, "ap-southeast-4": B, "ap-southeast-5": B, "ap-southeast-7": B, "ca-central-1": B, "ca-west-1": B, "eu-central-1": B, "eu-central-2": B, "eu-north-1": B, "eu-south-1": B, "eu-south-2": B, "eu-west-1": B, "eu-west-2": B, "eu-west-3": B, "il-central-1": B, "me-central-1": B, "me-south-1": B, "mx-central-1": B, "sa-east-1": B, "us-east-1": Le, "us-east-2": Le, "us-gov-east-1": et, "us-gov-west-1": et, "us-west-1": Le, "us-west-2": Le }], amplifyapp: t, awsapprunner: a, awsapps: t, elasticbeanstalk: [2, { "af-south-1": t, "ap-east-1": t, "ap-northeast-1": t, "ap-northeast-2": t, "ap-northeast-3": t, "ap-south-1": t, "ap-southeast-1": t, "ap-southeast-2": t, "ap-southeast-3": t, "ap-southeast-5": t, "ap-southeast-7": t, "ca-central-1": t, "eu-central-1": t, "eu-north-1": t, "eu-south-1": t, "eu-south-2": t, "eu-west-1": t, "eu-west-2": t, "eu-west-3": t, "il-central-1": t, "me-central-1": t, "me-south-1": t, "sa-east-1": t, "us-east-1": t, "us-east-2": t, "us-gov-east-1": t, "us-gov-west-1": t, "us-west-1": t, "us-west-2": t }], awsglobalaccelerator: t, siiites: t, appspacehosted: t, appspaceusercontent: t, "on-aptible": t, myasustor: t, "balena-devices": t, boutir: t, bplaced: t, cafjs: t, "canva-apps": t, "canva-hosted-embed": t, canvacode: t, "rice-labs": t, "cdn77-storage": t, br: t, cn: t, de: t, eu: t, jpn: t, mex: t, ru: t, sa: t, uk: t, us: t, za: t, "clever-cloud": [0, { services: a }], abrdns: t, dnsabr: t, "ip-ddns": t, jdevcloud: t, wpdevcloud: t, "cf-ipfs": t, "cloudflare-ipfs": t, trycloudflare: t, co: t, devinapps: a, builtwithdark: t, datadetect: [0, { demo: t, instance: t }], dattolocal: t, dattorelay: t, dattoweb: t, mydatto: t, digitaloceanspaces: a, discordsays: t, discordsez: t, drayddns: t, dreamhosters: t, durumis: t, blogdns: t, cechire: t, dnsalias: t, dnsdojo: t, doesntexist: t, dontexist: t, doomdns: t, "dyn-o-saur": t, dynalias: t, "dyndns-at-home": t, "dyndns-at-work": t, "dyndns-blog": t, "dyndns-free": t, "dyndns-home": t, "dyndns-ip": t, "dyndns-mail": t, "dyndns-office": t, "dyndns-pics": t, "dyndns-remote": t, "dyndns-server": t, "dyndns-web": t, "dyndns-wiki": t, "dyndns-work": t, "est-a-la-maison": t, "est-a-la-masion": t, "est-le-patron": t, "est-mon-blogueur": t, "from-ak": t, "from-al": t, "from-ar": t, "from-ca": t, "from-ct": t, "from-dc": t, "from-de": t, "from-fl": t, "from-ga": t, "from-hi": t, "from-ia": t, "from-id": t, "from-il": t, "from-in": t, "from-ks": t, "from-ky": t, "from-ma": t, "from-md": t, "from-mi": t, "from-mn": t, "from-mo": t, "from-ms": t, "from-mt": t, "from-nc": t, "from-nd": t, "from-ne": t, "from-nh": t, "from-nj": t, "from-nm": t, "from-nv": t, "from-oh": t, "from-ok": t, "from-or": t, "from-pa": t, "from-pr": t, "from-ri": t, "from-sc": t, "from-sd": t, "from-tn": t, "from-tx": t, "from-ut": t, "from-va": t, "from-vt": t, "from-wa": t, "from-wi": t, "from-wv": t, "from-wy": t, getmyip: t, gotdns: t, "hobby-site": t, homelinux: t, homeunix: t, iamallama: t, "is-a-anarchist": t, "is-a-blogger": t, "is-a-bookkeeper": t, "is-a-bulls-fan": t, "is-a-caterer": t, "is-a-chef": t, "is-a-conservative": t, "is-a-cpa": t, "is-a-cubicle-slave": t, "is-a-democrat": t, "is-a-designer": t, "is-a-doctor": t, "is-a-financialadvisor": t, "is-a-geek": t, "is-a-green": t, "is-a-guru": t, "is-a-hard-worker": t, "is-a-hunter": t, "is-a-landscaper": t, "is-a-lawyer": t, "is-a-liberal": t, "is-a-libertarian": t, "is-a-llama": t, "is-a-musician": t, "is-a-nascarfan": t, "is-a-nurse": t, "is-a-painter": t, "is-a-personaltrainer": t, "is-a-photographer": t, "is-a-player": t, "is-a-republican": t, "is-a-rockstar": t, "is-a-socialist": t, "is-a-student": t, "is-a-teacher": t, "is-a-techie": t, "is-a-therapist": t, "is-an-accountant": t, "is-an-actor": t, "is-an-actress": t, "is-an-anarchist": t, "is-an-artist": t, "is-an-engineer": t, "is-an-entertainer": t, "is-certified": t, "is-gone": t, "is-into-anime": t, "is-into-cars": t, "is-into-cartoons": t, "is-into-games": t, "is-leet": t, "is-not-certified": t, "is-slick": t, "is-uberleet": t, "is-with-theband": t, "isa-geek": t, "isa-hockeynut": t, issmarterthanyou: t, "likes-pie": t, likescandy: t, "neat-url": t, "saves-the-whales": t, selfip: t, "sells-for-less": t, "sells-for-u": t, servebbs: t, "simple-url": t, "space-to-rent": t, "teaches-yoga": t, writesthisblog: t, "1cooldns": t, bumbleshrimp: t, ddnsfree: t, ddnsgeek: t, ddnsguru: t, dynuddns: t, dynuhosting: t, giize: t, gleeze: t, kozow: t, loseyourip: t, ooguy: t, pivohosting: t, theworkpc: t, wiredbladehosting: t, emergentagent: [0, { preview: t }], mytuleap: t, "tuleap-partners": t, encoreapi: t, evennode: [0, { "eu-1": t, "eu-2": t, "eu-3": t, "eu-4": t, "us-1": t, "us-2": t, "us-3": t, "us-4": t }], onfabrica: t, "fastly-edge": t, "fastly-terrarium": t, "fastvps-server": t, mydobiss: t, firebaseapp: t, fldrv: t, framercanvas: t, "freebox-os": t, freeboxos: t, freemyip: t, aliases121: t, gentapps: t, gentlentapis: t, githubusercontent: t, "0emm": a, appspot: [2, { r: a }], blogspot: t, codespot: t, googleapis: t, googlecode: t, pagespeedmobilizer: t, withgoogle: t, withyoutube: t, grayjayleagues: t, hatenablog: t, hatenadiary: t, "hercules-app": t, "hercules-dev": t, herokuapp: t, gr: t, smushcdn: t, wphostedmail: t, wpmucdn: t, pixolino: t, "apps-1and1": t, "live-website": t, "webspace-host": t, dopaas: t, "hosted-by-previder": Ie, hosteur: [0, { "rag-cloud": t, "rag-cloud-ch": t }], "ik-server": [0, { jcloud: t, "jcloud-ver-jpc": t }], jelastic: [0, { demo: t }], massivegrid: Ie, wafaicloud: [0, { jed: t, ryd: t }], "eu1-plenit": t, "la1-plenit": t, "us1-plenit": t, webadorsite: t, "on-forge": t, "on-vapor": t, lpusercontent: t, linode: [0, { members: t, nodebalancer: a }], linodeobjects: a, linodeusercontent: [0, { ip: t }], localtonet: t, lovableproject: t, barsycenter: t, barsyonline: t, lutrausercontent: a, magicpatternsapp: t, modelscape: t, mwcloudnonprod: t, polyspace: t, mazeplay: t, miniserver: t, atmeta: t, fbsbx: tt, meteorapp: S, routingthecloud: t, "same-app": t, "same-preview": t, mydbserver: t, mochausercontent: t, hostedpi: t, "mythic-beasts": [0, { caracal: t, customer: t, fentiger: t, lynx: t, ocelot: t, oncilla: t, onza: t, sphinx: t, vs: t, x: t, yali: t }], nospamproxy: [0, { cloud: [2, { o365: t }] }], "4u": t, nfshost: t, "3utilities": t, blogsyte: t, ciscofreak: t, damnserver: t, ddnsking: t, ditchyourip: t, dnsiskinky: t, dynns: t, geekgalaxy: t, "health-carereform": t, homesecuritymac: t, homesecuritypc: t, myactivedirectory: t, mysecuritycamera: t, myvnc: t, "net-freaks": t, onthewifi: t, point2this: t, quicksytes: t, securitytactics: t, servebeer: t, servecounterstrike: t, serveexchange: t, serveftp: t, servegame: t, servehalflife: t, servehttp: t, servehumour: t, serveirc: t, servemp3: t, servep2p: t, servepics: t, servequake: t, servesarcasm: t, stufftoread: t, unusualperson: t, workisboring: t, myiphost: t, observableusercontent: [0, { static: t }], simplesite: t, oaiusercontent: a, orsites: t, operaunite: t, "customer-oci": [0, { "*": t, oci: a, ocp: a, ocs: a }], oraclecloudapps: a, oraclegovcloudapps: a, "authgear-staging": t, authgearapps: t, outsystemscloud: t, ownprovider: t, pgfog: t, pagexl: t, gotpantheon: t, paywhirl: a, forgeblocks: t, upsunapp: t, "postman-echo": t, prgmr: [0, { xen: t }], "project-study": [0, { dev: t }], pythonanywhere: S, qa2: t, "alpha-myqnapcloud": t, "dev-myqnapcloud": t, mycloudnas: t, mynascloud: t, myqnapcloud: t, qualifioapp: t, ladesk: t, qualyhqpartner: a, qualyhqportal: a, qbuser: t, quipelements: a, rackmaze: t, "readthedocs-hosted": t, rhcloud: t, onrender: t, render: Rt, "subsc-pay": t, "180r": t, dojin: t, sakuratan: t, sakuraweb: t, x0: t, code: [0, { builder: a, "dev-builder": a, "stg-builder": a }], salesforce: [0, { platform: [0, { "code-builder-stg": [0, { test: [0, { "001": a }] }] }] }], logoip: t, scrysec: t, "firewall-gateway": t, myshopblocks: t, myshopify: t, shopitsite: t, "1kapp": t, appchizi: t, applinzi: t, sinaapp: t, vipsinaapp: t, streamlitapp: t, "try-snowplow": t, "playstation-cloud": t, myspreadshop: t, "w-corp-staticblitz": t, "w-credentialless-staticblitz": t, "w-staticblitz": t, "stackhero-network": t, stdlib: [0, { api: t }], strapiapp: [2, { media: t }], "streak-link": t, streaklinks: t, streakusercontent: t, "temp-dns": t, dsmynas: t, familyds: t, mytabit: t, taveusercontent: t, "tb-hosting": Ce, reservd: t, thingdustdata: t, "townnews-staging": t, typeform: [0, { pro: t }], hk: t, it: t, "deus-canvas": t, vultrobjects: a, wafflecell: t, hotelwithflight: t, "reserve-online": t, cprapid: t, pleskns: t, remotewd: t, wiardweb: [0, { pages: t }], "base44-sandbox": t, wixsite: t, wixstudio: t, messwithdns: t, "woltlab-demo": t, wpenginepowered: [2, { js: t }], xnbay: [2, { u2: t, "u2-local": t }], xtooldevice: t, yolasite: t }], coop: e, cr: [1, { ac: e, co: e, ed: e, fi: e, go: e, or: e, sa: e }], cu: [1, { com: e, edu: e, gob: e, inf: e, nat: e, net: e, org: e }], cv: [1, { com: e, edu: e, id: e, int: e, net: e, nome: e, org: e, publ: e }], cw: Te, cx: [1, { gov: e, cloudns: t, ath: t, info: t, assessments: t, calculators: t, funnels: t, paynow: t, quizzes: t, researched: t, tests: t }], cy: [1, { ac: e, biz: e, com: [1, { scaleforce: fe }], ekloges: e, gov: e, ltd: e, mil: e, net: e, org: e, press: e, pro: e, tm: e }], cz: [1, { gov: e, contentproxy9: [0, { rsc: t }], realm: t, e4: t, co: t, metacentrum: [0, { cloud: a, custom: t }], muni: [0, { cloud: [0, { flt: t, usr: t }] }] }], de: [1, { bplaced: t, square7: t, "bwcloud-os-instance": a, com: t, cosidns: Fe, dnsupdater: t, "dynamisches-dns": t, "internet-dns": t, "l-o-g-i-n": t, ddnss: [2, { dyn: t, dyndns: t }], "dyn-ip24": t, dyndns1: t, "home-webserver": [2, { dyn: t }], "myhome-server": t, dnshome: t, fuettertdasnetz: t, isteingeek: t, istmein: t, lebtimnetz: t, leitungsen: t, traeumtgerade: t, frusky: a, goip: t, "xn--gnstigbestellen-zvb": t, günstigbestellen: t, "xn--gnstigliefern-wob": t, günstigliefern: t, "hs-heilbronn": [0, { it: [0, { pages: t, "pages-research": t }] }], "dyn-berlin": t, "in-berlin": t, "in-brb": t, "in-butter": t, "in-dsl": t, "in-vpn": t, iservschule: t, "mein-iserv": t, schuldock: t, schulplattform: t, schulserver: t, "test-iserv": t, keymachine: t, co: t, "git-repos": t, "lcube-server": t, "svn-repos": t, barsy: t, webspaceconfig: t, "123webseite": t, rub: t, "ruhr-uni-bochum": [2, { noc: [0, { io: t }] }], logoip: t, "firewall-gateway": t, "my-gateway": t, "my-router": t, spdns: t, my: t, speedpartner: [0, { customer: t }], myspreadshop: t, "taifun-dns": t, "12hp": t, "2ix": t, "4lima": t, "lima-city": t, "virtual-user": t, virtualuser: t, "community-pro": t, diskussionsbereich: t, xenonconnect: a }], dj: e, dk: [1, { biz: t, co: t, firm: t, reg: t, store: t, "123hjemmeside": t, myspreadshop: t }], dm: ae, do: [1, { art: e, com: e, edu: e, gob: e, gov: e, mil: e, net: e, org: e, sld: e, web: e }], dz: [1, { art: e, asso: e, com: e, edu: e, gov: e, net: e, org: e, pol: e, soc: e, tm: e }], ec: [1, { abg: e, adm: e, agron: e, arqt: e, art: e, bar: e, chef: e, com: e, cont: e, cpa: e, cue: e, dent: e, dgn: e, disco: e, doc: e, edu: e, eng: e, esm: e, fin: e, fot: e, gal: e, gob: e, gov: e, gye: e, ibr: e, info: e, k12: e, lat: e, loj: e, med: e, mil: e, mktg: e, mon: e, net: e, ntr: e, odont: e, org: e, pro: e, prof: e, psic: e, psiq: e, pub: e, rio: e, rrpp: e, sal: e, tech: e, tul: e, tur: e, uio: e, vet: e, xxx: e, base: t, official: t }], edu: [1, { rit: [0, { "git-pages": t }] }], ee: [1, { aip: e, com: e, edu: e, fie: e, gov: e, lib: e, med: e, org: e, pri: e, riik: e }], eg: [1, { ac: e, com: e, edu: e, eun: e, gov: e, info: e, me: e, mil: e, name: e, net: e, org: e, sci: e, sport: e, tv: e }], er: w, es: [1, { com: e, edu: e, gob: e, nom: e, org: e, "123miweb": t, myspreadshop: t }], et: [1, { biz: e, com: e, edu: e, gov: e, info: e, name: e, net: e, org: e }], eu: [1, { amazonwebservices: [0, { on: [0, { "eusc-de-east-1": [0, { "cognito-idp": B }] }] }], cloudns: t, prvw: t, deuxfleurs: t, dogado: [0, { jelastic: t }], barsy: t, spdns: t, nxa: a, directwp: t, transurl: a }], fi: [1, { aland: e, dy: t, "xn--hkkinen-5wa": t, häkkinen: t, iki: t, cloudplatform: [0, { fi: t }], datacenter: [0, { demo: t, paas: t }], kapsi: t, "123kotisivu": t, myspreadshop: t }], fj: [1, { ac: e, biz: e, com: e, edu: e, gov: e, id: e, info: e, mil: e, name: e, net: e, org: e, pro: e }], fk: w, fm: [1, { com: e, edu: e, net: e, org: e, radio: t, user: a }], fo: e, fr: [1, { asso: e, com: e, gouv: e, nom: e, prd: e, tm: e, avoues: e, cci: e, greta: e, "huissier-justice": e, "fbx-os": t, fbxos: t, "freebox-os": t, freeboxos: t, goupile: t, "123siteweb": t, "on-web": t, "chirurgiens-dentistes-en-france": t, dedibox: t, aeroport: t, avocat: t, chambagri: t, "chirurgiens-dentistes": t, "experts-comptables": t, medecin: t, notaires: t, pharmacien: t, port: t, veterinaire: t, myspreadshop: t, ynh: t }], ga: e, gb: e, gd: [1, { edu: e, gov: e }], ge: [1, { com: e, edu: e, gov: e, net: e, org: e, pvt: e, school: e }], gf: e, gg: [1, { co: e, net: e, org: e, ply: [0, { at: a, d6: t }], botdash: t, kaas: t, stackit: t, panel: [2, { daemon: t }] }], gh: [1, { biz: e, com: e, edu: e, gov: e, mil: e, net: e, org: e }], gi: [1, { com: e, edu: e, gov: e, ltd: e, mod: e, org: e }], gl: [1, { co: e, com: e, edu: e, net: e, org: e }], gm: e, gn: [1, { ac: e, com: e, edu: e, gov: e, net: e, org: e }], gov: e, gp: [1, { asso: e, com: e, edu: e, mobi: e, net: e, org: e }], gq: e, gr: [1, { com: e, edu: e, gov: e, net: e, org: e, barsy: t, simplesite: t }], gs: e, gt: [1, { com: e, edu: e, gob: e, ind: e, mil: e, net: e, org: e }], gu: [1, { com: e, edu: e, gov: e, guam: e, info: e, net: e, org: e, web: e }], gw: [1, { nx: t }], gy: ae, hk: [1, { com: e, edu: e, gov: e, idv: e, net: e, org: e, "xn--ciqpn": e, 个人: e, "xn--gmqw5a": e, 個人: e, "xn--55qx5d": e, 公司: e, "xn--mxtq1m": e, 政府: e, "xn--lcvr32d": e, 敎育: e, "xn--wcvs22d": e, 教育: e, "xn--gmq050i": e, 箇人: e, "xn--uc0atv": e, 組織: e, "xn--uc0ay4a": e, 組织: e, "xn--od0alg": e, 網絡: e, "xn--zf0avx": e, 網络: e, "xn--mk0axi": e, 组織: e, "xn--tn0ag": e, 组织: e, "xn--od0aq3b": e, 网絡: e, "xn--io0a7i": e, 网络: e, inc: t, ltd: t }], hm: e, hn: [1, { com: e, edu: e, gob: e, mil: e, net: e, org: e }], hr: [1, { com: e, from: e, iz: e, name: e, brendly: f }], ht: [1, { adult: e, art: e, asso: e, com: e, coop: e, edu: e, firm: e, gouv: e, info: e, med: e, net: e, org: e, perso: e, pol: e, pro: e, rel: e, shop: e, rt: t }], hu: [1, { 2e3: e, agrar: e, bolt: e, casino: e, city: e, co: e, erotica: e, erotika: e, film: e, forum: e, games: e, hotel: e, info: e, ingatlan: e, jogasz: e, konyvelo: e, lakas: e, media: e, news: e, org: e, priv: e, reklam: e, sex: e, shop: e, sport: e, suli: e, szex: e, tm: e, tozsde: e, utazas: e, video: e }], id: [1, { ac: e, biz: e, co: e, desa: e, go: e, kop: e, mil: e, my: e, net: e, or: e, ponpes: e, sch: e, web: e, "xn--9tfky": e, "ᬩᬮᬶ": e, e: t, zone: t }], ie: [1, { gov: e, myspreadshop: t }], il: [1, { ac: e, co: [1, { ravpage: t, mytabit: t, tabitorder: t }], gov: e, idf: e, k12: e, muni: e, net: e, org: e }], "xn--4dbrk0ce": [1, { "xn--4dbgdty6c": e, "xn--5dbhl8d": e, "xn--8dbq2a": e, "xn--hebda8b": e }], ישראל: [1, { אקדמיה: e, ישוב: e, צהל: e, ממשל: e }], im: [1, { ac: e, co: [1, { ltd: e, plc: e }], com: e, net: e, org: e, tt: e, tv: e }], in: [1, { "5g": e, "6g": e, ac: e, ai: e, am: e, bank: e, bihar: e, biz: e, business: e, ca: e, cn: e, co: e, com: e, coop: e, cs: e, delhi: e, dr: e, edu: e, er: e, fin: e, firm: e, gen: e, gov: e, gujarat: e, ind: e, info: e, int: e, internet: e, io: e, me: e, mil: e, net: e, nic: e, org: e, pg: e, post: e, pro: e, res: e, travel: e, tv: e, uk: e, up: e, us: e, cloudns: t, barsy: t, web: t, indevs: t, supabase: t }], info: [1, { cloudns: t, "dynamic-dns": t, "barrel-of-knowledge": t, "barrell-of-knowledge": t, dyndns: t, "for-our": t, "groks-the": t, "groks-this": t, "here-for-more": t, knowsitall: t, selfip: t, webhop: t, barsy: t, mayfirst: t, mittwald: t, mittwaldserver: t, typo3server: t, dvrcam: t, ilovecollege: t, "no-ip": t, forumz: t, nsupdate: t, dnsupdate: t, "v-info": t }], int: [1, { eu: e }], io: [1, { 2038: t, co: e, com: e, edu: e, gov: e, mil: e, net: e, nom: e, org: e, "on-acorn": a, myaddr: t, apigee: t, "b-data": t, beagleboard: t, bitbucket: t, bluebite: t, boxfuse: t, brave: i, browsersafetymark: t, bubble: ge, bubbleapps: t, bigv: [0, { uk0: t }], cleverapps: t, cloudbeesusercontent: t, dappnode: [0, { dyndns: t }], darklang: t, definima: t, dedyn: t, icp0: Re, icp1: Re, qzz: t, "fh-muenster": t, gitbook: t, github: t, gitlab: t, lolipop: t, "hasura-app": t, hostyhosting: t, hypernode: t, moonscale: a, beebyte: Ie, beebyteapp: [0, { sekd1: t }], jele: t, keenetic: t, kiloapps: t, webthings: t, loginline: t, barsy: t, azurecontainer: a, ngrok: [2, { ap: t, au: t, eu: t, in: t, jp: t, sa: t, us: t }], nodeart: [0, { stage: t }], pantheonsite: t, forgerock: [0, { id: t }], pstmn: [2, { mock: t }], protonet: t, qcx: [2, { sys: a }], qoto: t, vaporcloud: t, myrdbx: t, "rb-hosting": Ce, "on-k3s": a, "on-rio": a, readthedocs: t, resindevice: t, resinstaging: [0, { devices: t }], hzc: t, sandcats: t, scrypted: [0, { client: t }], "mo-siemens": t, lair: tt, stolos: a, musician: t, utwente: t, edugit: t, telebit: t, thingdust: [0, { dev: ye, disrec: ye, prod: U, testing: ye }], tickets: t, webflow: t, webflowtest: t, editorx: t, wixstudio: t, basicserver: t, virtualserver: t }], iq: r, ir: [1, { ac: e, co: e, gov: e, id: e, net: e, org: e, sch: e, "xn--mgba3a4f16a": e, ایران: e, "xn--mgba3a4fra": e, ايران: e, arvanedge: t, vistablog: t }], is: e, it: [1, { edu: e, gov: e, abr: e, abruzzo: e, "aosta-valley": e, aostavalley: e, bas: e, basilicata: e, cal: e, calabria: e, cam: e, campania: e, "emilia-romagna": e, emiliaromagna: e, emr: e, "friuli-v-giulia": e, "friuli-ve-giulia": e, "friuli-vegiulia": e, "friuli-venezia-giulia": e, "friuli-veneziagiulia": e, "friuli-vgiulia": e, "friuliv-giulia": e, "friulive-giulia": e, friulivegiulia: e, "friulivenezia-giulia": e, friuliveneziagiulia: e, friulivgiulia: e, fvg: e, laz: e, lazio: e, lig: e, liguria: e, lom: e, lombardia: e, lombardy: e, lucania: e, mar: e, marche: e, mol: e, molise: e, piedmont: e, piemonte: e, pmn: e, pug: e, puglia: e, sar: e, sardegna: e, sardinia: e, sic: e, sicilia: e, sicily: e, taa: e, tos: e, toscana: e, "trentin-sud-tirol": e, "xn--trentin-sd-tirol-rzb": e, "trentin-süd-tirol": e, "trentin-sudtirol": e, "xn--trentin-sdtirol-7vb": e, "trentin-südtirol": e, "trentin-sued-tirol": e, "trentin-suedtirol": e, trentino: e, "trentino-a-adige": e, "trentino-aadige": e, "trentino-alto-adige": e, "trentino-altoadige": e, "trentino-s-tirol": e, "trentino-stirol": e, "trentino-sud-tirol": e, "xn--trentino-sd-tirol-c3b": e, "trentino-süd-tirol": e, "trentino-sudtirol": e, "xn--trentino-sdtirol-szb": e, "trentino-südtirol": e, "trentino-sued-tirol": e, "trentino-suedtirol": e, "trentinoa-adige": e, trentinoaadige: e, "trentinoalto-adige": e, trentinoaltoadige: e, "trentinos-tirol": e, trentinostirol: e, "trentinosud-tirol": e, "xn--trentinosd-tirol-rzb": e, "trentinosüd-tirol": e, trentinosudtirol: e, "xn--trentinosdtirol-7vb": e, trentinosüdtirol: e, "trentinosued-tirol": e, trentinosuedtirol: e, "trentinsud-tirol": e, "xn--trentinsd-tirol-6vb": e, "trentinsüd-tirol": e, trentinsudtirol: e, "xn--trentinsdtirol-nsb": e, trentinsüdtirol: e, "trentinsued-tirol": e, trentinsuedtirol: e, tuscany: e, umb: e, umbria: e, "val-d-aosta": e, "val-daosta": e, "vald-aosta": e, valdaosta: e, "valle-aosta": e, "valle-d-aosta": e, "valle-daosta": e, valleaosta: e, "valled-aosta": e, valledaosta: e, "vallee-aoste": e, "xn--valle-aoste-ebb": e, "vallée-aoste": e, "vallee-d-aoste": e, "xn--valle-d-aoste-ehb": e, "vallée-d-aoste": e, valleeaoste: e, "xn--valleaoste-e7a": e, valléeaoste: e, valleedaoste: e, "xn--valledaoste-ebb": e, valléedaoste: e, vao: e, vda: e, ven: e, veneto: e, ag: e, agrigento: e, al: e, alessandria: e, "alto-adige": e, altoadige: e, an: e, ancona: e, "andria-barletta-trani": e, "andria-trani-barletta": e, andriabarlettatrani: e, andriatranibarletta: e, ao: e, aosta: e, aoste: e, ap: e, aq: e, aquila: e, ar: e, arezzo: e, "ascoli-piceno": e, ascolipiceno: e, asti: e, at: e, av: e, avellino: e, ba: e, balsan: e, "balsan-sudtirol": e, "xn--balsan-sdtirol-nsb": e, "balsan-südtirol": e, "balsan-suedtirol": e, bari: e, "barletta-trani-andria": e, barlettatraniandria: e, belluno: e, benevento: e, bergamo: e, bg: e, bi: e, biella: e, bl: e, bn: e, bo: e, bologna: e, bolzano: e, "bolzano-altoadige": e, bozen: e, "bozen-sudtirol": e, "xn--bozen-sdtirol-2ob": e, "bozen-südtirol": e, "bozen-suedtirol": e, br: e, brescia: e, brindisi: e, bs: e, bt: e, bulsan: e, "bulsan-sudtirol": e, "xn--bulsan-sdtirol-nsb": e, "bulsan-südtirol": e, "bulsan-suedtirol": e, bz: e, ca: e, cagliari: e, caltanissetta: e, "campidano-medio": e, campidanomedio: e, campobasso: e, "carbonia-iglesias": e, carboniaiglesias: e, "carrara-massa": e, carraramassa: e, caserta: e, catania: e, catanzaro: e, cb: e, ce: e, "cesena-forli": e, "xn--cesena-forl-mcb": e, "cesena-forlì": e, cesenaforli: e, "xn--cesenaforl-i8a": e, cesenaforlì: e, ch: e, chieti: e, ci: e, cl: e, cn: e, co: e, como: e, cosenza: e, cr: e, cremona: e, crotone: e, cs: e, ct: e, cuneo: e, cz: e, "dell-ogliastra": e, dellogliastra: e, en: e, enna: e, fc: e, fe: e, fermo: e, ferrara: e, fg: e, fi: e, firenze: e, florence: e, fm: e, foggia: e, "forli-cesena": e, "xn--forl-cesena-fcb": e, "forlì-cesena": e, forlicesena: e, "xn--forlcesena-c8a": e, forlìcesena: e, fr: e, frosinone: e, ge: e, genoa: e, genova: e, go: e, gorizia: e, gr: e, grosseto: e, "iglesias-carbonia": e, iglesiascarbonia: e, im: e, imperia: e, is: e, isernia: e, kr: e, "la-spezia": e, laquila: e, laspezia: e, latina: e, lc: e, le: e, lecce: e, lecco: e, li: e, livorno: e, lo: e, lodi: e, lt: e, lu: e, lucca: e, macerata: e, mantova: e, "massa-carrara": e, massacarrara: e, matera: e, mb: e, mc: e, me: e, "medio-campidano": e, mediocampidano: e, messina: e, mi: e, milan: e, milano: e, mn: e, mo: e, modena: e, monza: e, "monza-brianza": e, "monza-e-della-brianza": e, monzabrianza: e, monzaebrianza: e, monzaedellabrianza: e, ms: e, mt: e, na: e, naples: e, napoli: e, no: e, novara: e, nu: e, nuoro: e, og: e, ogliastra: e, "olbia-tempio": e, olbiatempio: e, or: e, oristano: e, ot: e, pa: e, padova: e, padua: e, palermo: e, parma: e, pavia: e, pc: e, pd: e, pe: e, perugia: e, "pesaro-urbino": e, pesarourbino: e, pescara: e, pg: e, pi: e, piacenza: e, pisa: e, pistoia: e, pn: e, po: e, pordenone: e, potenza: e, pr: e, prato: e, pt: e, pu: e, pv: e, pz: e, ra: e, ragusa: e, ravenna: e, rc: e, re: e, "reggio-calabria": e, "reggio-emilia": e, reggiocalabria: e, reggioemilia: e, rg: e, ri: e, rieti: e, rimini: e, rm: e, rn: e, ro: e, roma: e, rome: e, rovigo: e, sa: e, salerno: e, sassari: e, savona: e, si: e, siena: e, siracusa: e, so: e, sondrio: e, sp: e, sr: e, ss: e, "xn--sdtirol-n2a": e, südtirol: e, suedtirol: e, sv: e, ta: e, taranto: e, te: e, "tempio-olbia": e, tempioolbia: e, teramo: e, terni: e, tn: e, to: e, torino: e, tp: e, tr: e, "trani-andria-barletta": e, "trani-barletta-andria": e, traniandriabarletta: e, tranibarlettaandria: e, trapani: e, trento: e, treviso: e, trieste: e, ts: e, turin: e, tv: e, ud: e, udine: e, "urbino-pesaro": e, urbinopesaro: e, va: e, varese: e, vb: e, vc: e, ve: e, venezia: e, venice: e, verbania: e, vercelli: e, verona: e, vi: e, "vibo-valentia": e, vibovalentia: e, vicenza: e, viterbo: e, vr: e, vs: e, vt: e, vv: e, ibxos: t, iliadboxos: t, neen: [0, { jc: t }], "123homepage": t, "16-b": t, "32-b": t, "64-b": t, myspreadshop: t, syncloud: t }], je: [1, { co: e, net: e, org: e, of: t }], jm: w, jo: [1, { agri: e, ai: e, com: e, edu: e, eng: e, fm: e, gov: e, mil: e, net: e, org: e, per: e, phd: e, sch: e, tv: e }], jobs: e, jp: [1, { ac: e, ad: e, co: e, ed: e, go: e, gr: e, lg: e, ne: [1, { aseinet: Nt, gehirn: t, ivory: t, "mail-box": t, mints: t, mokuren: t, opal: t, sakura: t, sumomo: t, topaz: t }], or: e, aichi: [1, { aisai: e, ama: e, anjo: e, asuke: e, chiryu: e, chita: e, fuso: e, gamagori: e, handa: e, hazu: e, hekinan: e, higashiura: e, ichinomiya: e, inazawa: e, inuyama: e, isshiki: e, iwakura: e, kanie: e, kariya: e, kasugai: e, kira: e, kiyosu: e, komaki: e, konan: e, kota: e, mihama: e, miyoshi: e, nishio: e, nisshin: e, obu: e, oguchi: e, oharu: e, okazaki: e, owariasahi: e, seto: e, shikatsu: e, shinshiro: e, shitara: e, tahara: e, takahama: e, tobishima: e, toei: e, togo: e, tokai: e, tokoname: e, toyoake: e, toyohashi: e, toyokawa: e, toyone: e, toyota: e, tsushima: e, yatomi: e }], akita: [1, { akita: e, daisen: e, fujisato: e, gojome: e, hachirogata: e, happou: e, higashinaruse: e, honjo: e, honjyo: e, ikawa: e, kamikoani: e, kamioka: e, katagami: e, kazuno: e, kitaakita: e, kosaka: e, kyowa: e, misato: e, mitane: e, moriyoshi: e, nikaho: e, noshiro: e, odate: e, oga: e, ogata: e, semboku: e, yokote: e, yurihonjo: e }], aomori: [1, { aomori: e, gonohe: e, hachinohe: e, hashikami: e, hiranai: e, hirosaki: e, itayanagi: e, kuroishi: e, misawa: e, mutsu: e, nakadomari: e, noheji: e, oirase: e, owani: e, rokunohe: e, sannohe: e, shichinohe: e, shingo: e, takko: e, towada: e, tsugaru: e, tsuruta: e }], chiba: [1, { abiko: e, asahi: e, chonan: e, chosei: e, choshi: e, chuo: e, funabashi: e, futtsu: e, hanamigawa: e, ichihara: e, ichikawa: e, ichinomiya: e, inzai: e, isumi: e, kamagaya: e, kamogawa: e, kashiwa: e, katori: e, katsuura: e, kimitsu: e, kisarazu: e, kozaki: e, kujukuri: e, kyonan: e, matsudo: e, midori: e, mihama: e, minamiboso: e, mobara: e, mutsuzawa: e, nagara: e, nagareyama: e, narashino: e, narita: e, noda: e, oamishirasato: e, omigawa: e, onjuku: e, otaki: e, sakae: e, sakura: e, shimofusa: e, shirako: e, shiroi: e, shisui: e, sodegaura: e, sosa: e, tako: e, tateyama: e, togane: e, tohnosho: e, tomisato: e, urayasu: e, yachimata: e, yachiyo: e, yokaichiba: e, yokoshibahikari: e, yotsukaido: e }], ehime: [1, { ainan: e, honai: e, ikata: e, imabari: e, iyo: e, kamijima: e, kihoku: e, kumakogen: e, masaki: e, matsuno: e, matsuyama: e, namikata: e, niihama: e, ozu: e, saijo: e, seiyo: e, shikokuchuo: e, tobe: e, toon: e, uchiko: e, uwajima: e, yawatahama: e }], fukui: [1, { echizen: e, eiheiji: e, fukui: e, ikeda: e, katsuyama: e, mihama: e, minamiechizen: e, obama: e, ohi: e, ono: e, sabae: e, sakai: e, takahama: e, tsuruga: e, wakasa: e }], fukuoka: [1, { ashiya: e, buzen: e, chikugo: e, chikuho: e, chikujo: e, chikushino: e, chikuzen: e, chuo: e, dazaifu: e, fukuchi: e, hakata: e, higashi: e, hirokawa: e, hisayama: e, iizuka: e, inatsuki: e, kaho: e, kasuga: e, kasuya: e, kawara: e, keisen: e, koga: e, kurate: e, kurogi: e, kurume: e, minami: e, miyako: e, miyama: e, miyawaka: e, mizumaki: e, munakata: e, nakagawa: e, nakama: e, nishi: e, nogata: e, ogori: e, okagaki: e, okawa: e, oki: e, omuta: e, onga: e, onojo: e, oto: e, saigawa: e, sasaguri: e, shingu: e, shinyoshitomi: e, shonai: e, soeda: e, sue: e, tachiarai: e, tagawa: e, takata: e, toho: e, toyotsu: e, tsuiki: e, ukiha: e, umi: e, usui: e, yamada: e, yame: e, yanagawa: e, yukuhashi: e }], fukushima: [1, { aizubange: e, aizumisato: e, aizuwakamatsu: e, asakawa: e, bandai: e, date: e, fukushima: e, furudono: e, futaba: e, hanawa: e, higashi: e, hirata: e, hirono: e, iitate: e, inawashiro: e, ishikawa: e, iwaki: e, izumizaki: e, kagamiishi: e, kaneyama: e, kawamata: e, kitakata: e, kitashiobara: e, koori: e, koriyama: e, kunimi: e, miharu: e, mishima: e, namie: e, nango: e, nishiaizu: e, nishigo: e, okuma: e, omotego: e, ono: e, otama: e, samegawa: e, shimogo: e, shirakawa: e, showa: e, soma: e, sukagawa: e, taishin: e, tamakawa: e, tanagura: e, tenei: e, yabuki: e, yamato: e, yamatsuri: e, yanaizu: e, yugawa: e }], gifu: [1, { anpachi: e, ena: e, gifu: e, ginan: e, godo: e, gujo: e, hashima: e, hichiso: e, hida: e, higashishirakawa: e, ibigawa: e, ikeda: e, kakamigahara: e, kani: e, kasahara: e, kasamatsu: e, kawaue: e, kitagata: e, mino: e, minokamo: e, mitake: e, mizunami: e, motosu: e, nakatsugawa: e, ogaki: e, sakahogi: e, seki: e, sekigahara: e, shirakawa: e, tajimi: e, takayama: e, tarui: e, toki: e, tomika: e, wanouchi: e, yamagata: e, yaotsu: e, yoro: e }], gunma: [1, { annaka: e, chiyoda: e, fujioka: e, higashiagatsuma: e, isesaki: e, itakura: e, kanna: e, kanra: e, katashina: e, kawaba: e, kiryu: e, kusatsu: e, maebashi: e, meiwa: e, midori: e, minakami: e, naganohara: e, nakanojo: e, nanmoku: e, numata: e, oizumi: e, ora: e, ota: e, shibukawa: e, shimonita: e, shinto: e, showa: e, takasaki: e, takayama: e, tamamura: e, tatebayashi: e, tomioka: e, tsukiyono: e, tsumagoi: e, ueno: e, yoshioka: e }], hiroshima: [1, { asaminami: e, daiwa: e, etajima: e, fuchu: e, fukuyama: e, hatsukaichi: e, higashihiroshima: e, hongo: e, jinsekikogen: e, kaita: e, kui: e, kumano: e, kure: e, mihara: e, miyoshi: e, naka: e, onomichi: e, osakikamijima: e, otake: e, saka: e, sera: e, seranishi: e, shinichi: e, shobara: e, takehara: e }], hokkaido: [1, { abashiri: e, abira: e, aibetsu: e, akabira: e, akkeshi: e, asahikawa: e, ashibetsu: e, ashoro: e, assabu: e, atsuma: e, bibai: e, biei: e, bifuka: e, bihoro: e, biratori: e, chippubetsu: e, chitose: e, date: e, ebetsu: e, embetsu: e, eniwa: e, erimo: e, esan: e, esashi: e, fukagawa: e, fukushima: e, furano: e, furubira: e, haboro: e, hakodate: e, hamatonbetsu: e, hidaka: e, higashikagura: e, higashikawa: e, hiroo: e, hokuryu: e, hokuto: e, honbetsu: e, horokanai: e, horonobe: e, ikeda: e, imakane: e, ishikari: e, iwamizawa: e, iwanai: e, kamifurano: e, kamikawa: e, kamishihoro: e, kamisunagawa: e, kamoenai: e, kayabe: e, kembuchi: e, kikonai: e, kimobetsu: e, kitahiroshima: e, kitami: e, kiyosato: e, koshimizu: e, kunneppu: e, kuriyama: e, kuromatsunai: e, kushiro: e, kutchan: e, kyowa: e, mashike: e, matsumae: e, mikasa: e, minamifurano: e, mombetsu: e, moseushi: e, mukawa: e, muroran: e, naie: e, nakagawa: e, nakasatsunai: e, nakatombetsu: e, nanae: e, nanporo: e, nayoro: e, nemuro: e, niikappu: e, niki: e, nishiokoppe: e, noboribetsu: e, numata: e, obihiro: e, obira: e, oketo: e, okoppe: e, otaru: e, otobe: e, otofuke: e, otoineppu: e, oumu: e, ozora: e, pippu: e, rankoshi: e, rebun: e, rikubetsu: e, rishiri: e, rishirifuji: e, saroma: e, sarufutsu: e, shakotan: e, shari: e, shibecha: e, shibetsu: e, shikabe: e, shikaoi: e, shimamaki: e, shimizu: e, shimokawa: e, shinshinotsu: e, shintoku: e, shiranuka: e, shiraoi: e, shiriuchi: e, sobetsu: e, sunagawa: e, taiki: e, takasu: e, takikawa: e, takinoue: e, teshikaga: e, tobetsu: e, tohma: e, tomakomai: e, tomari: e, toya: e, toyako: e, toyotomi: e, toyoura: e, tsubetsu: e, tsukigata: e, urakawa: e, urausu: e, uryu: e, utashinai: e, wakkanai: e, wassamu: e, yakumo: e, yoichi: e }], hyogo: [1, { aioi: e, akashi: e, ako: e, amagasaki: e, aogaki: e, asago: e, ashiya: e, awaji: e, fukusaki: e, goshiki: e, harima: e, himeji: e, ichikawa: e, inagawa: e, itami: e, kakogawa: e, kamigori: e, kamikawa: e, kasai: e, kasuga: e, kawanishi: e, miki: e, minamiawaji: e, nishinomiya: e, nishiwaki: e, ono: e, sanda: e, sannan: e, sasayama: e, sayo: e, shingu: e, shinonsen: e, shiso: e, sumoto: e, taishi: e, taka: e, takarazuka: e, takasago: e, takino: e, tamba: e, tatsuno: e, toyooka: e, yabu: e, yashiro: e, yoka: e, yokawa: e }], ibaraki: [1, { ami: e, asahi: e, bando: e, chikusei: e, daigo: e, fujishiro: e, hitachi: e, hitachinaka: e, hitachiomiya: e, hitachiota: e, ibaraki: e, ina: e, inashiki: e, itako: e, iwama: e, joso: e, kamisu: e, kasama: e, kashima: e, kasumigaura: e, koga: e, miho: e, mito: e, moriya: e, naka: e, namegata: e, oarai: e, ogawa: e, omitama: e, ryugasaki: e, sakai: e, sakuragawa: e, shimodate: e, shimotsuma: e, shirosato: e, sowa: e, suifu: e, takahagi: e, tamatsukuri: e, tokai: e, tomobe: e, tone: e, toride: e, tsuchiura: e, tsukuba: e, uchihara: e, ushiku: e, yachiyo: e, yamagata: e, yawara: e, yuki: e }], ishikawa: [1, { anamizu: e, hakui: e, hakusan: e, kaga: e, kahoku: e, kanazawa: e, kawakita: e, komatsu: e, nakanoto: e, nanao: e, nomi: e, nonoichi: e, noto: e, shika: e, suzu: e, tsubata: e, tsurugi: e, uchinada: e, wajima: e }], iwate: [1, { fudai: e, fujisawa: e, hanamaki: e, hiraizumi: e, hirono: e, ichinohe: e, ichinoseki: e, iwaizumi: e, iwate: e, joboji: e, kamaishi: e, kanegasaki: e, karumai: e, kawai: e, kitakami: e, kuji: e, kunohe: e, kuzumaki: e, miyako: e, mizusawa: e, morioka: e, ninohe: e, noda: e, ofunato: e, oshu: e, otsuchi: e, rikuzentakata: e, shiwa: e, shizukuishi: e, sumita: e, tanohata: e, tono: e, yahaba: e, yamada: e }], kagawa: [1, { ayagawa: e, higashikagawa: e, kanonji: e, kotohira: e, manno: e, marugame: e, mitoyo: e, naoshima: e, sanuki: e, tadotsu: e, takamatsu: e, tonosho: e, uchinomi: e, utazu: e, zentsuji: e }], kagoshima: [1, { akune: e, amami: e, hioki: e, isa: e, isen: e, izumi: e, kagoshima: e, kanoya: e, kawanabe: e, kinko: e, kouyama: e, makurazaki: e, matsumoto: e, minamitane: e, nakatane: e, nishinoomote: e, satsumasendai: e, soo: e, tarumizu: e, yusui: e }], kanagawa: [1, { aikawa: e, atsugi: e, ayase: e, chigasaki: e, ebina: e, fujisawa: e, hadano: e, hakone: e, hiratsuka: e, isehara: e, kaisei: e, kamakura: e, kiyokawa: e, matsuda: e, minamiashigara: e, miura: e, nakai: e, ninomiya: e, odawara: e, oi: e, oiso: e, sagamihara: e, samukawa: e, tsukui: e, yamakita: e, yamato: e, yokosuka: e, yugawara: e, zama: e, zushi: e }], kochi: [1, { aki: e, geisei: e, hidaka: e, higashitsuno: e, ino: e, kagami: e, kami: e, kitagawa: e, kochi: e, mihara: e, motoyama: e, muroto: e, nahari: e, nakamura: e, nankoku: e, nishitosa: e, niyodogawa: e, ochi: e, okawa: e, otoyo: e, otsuki: e, sakawa: e, sukumo: e, susaki: e, tosa: e, tosashimizu: e, toyo: e, tsuno: e, umaji: e, yasuda: e, yusuhara: e }], kumamoto: [1, { amakusa: e, arao: e, aso: e, choyo: e, gyokuto: e, kamiamakusa: e, kikuchi: e, kumamoto: e, mashiki: e, mifune: e, minamata: e, minamioguni: e, nagasu: e, nishihara: e, oguni: e, ozu: e, sumoto: e, takamori: e, uki: e, uto: e, yamaga: e, yamato: e, yatsushiro: e }], kyoto: [1, { ayabe: e, fukuchiyama: e, higashiyama: e, ide: e, ine: e, joyo: e, kameoka: e, kamo: e, kita: e, kizu: e, kumiyama: e, kyotamba: e, kyotanabe: e, kyotango: e, maizuru: e, minami: e, minamiyamashiro: e, miyazu: e, muko: e, nagaokakyo: e, nakagyo: e, nantan: e, oyamazaki: e, sakyo: e, seika: e, tanabe: e, uji: e, ujitawara: e, wazuka: e, yamashina: e, yawata: e }], mie: [1, { asahi: e, inabe: e, ise: e, kameyama: e, kawagoe: e, kiho: e, kisosaki: e, kiwa: e, komono: e, kumano: e, kuwana: e, matsusaka: e, meiwa: e, mihama: e, minamiise: e, misugi: e, miyama: e, nabari: e, shima: e, suzuka: e, tado: e, taiki: e, taki: e, tamaki: e, toba: e, tsu: e, udono: e, ureshino: e, watarai: e, yokkaichi: e }], miyagi: [1, { furukawa: e, higashimatsushima: e, ishinomaki: e, iwanuma: e, kakuda: e, kami: e, kawasaki: e, marumori: e, matsushima: e, minamisanriku: e, misato: e, murata: e, natori: e, ogawara: e, ohira: e, onagawa: e, osaki: e, rifu: e, semine: e, shibata: e, shichikashuku: e, shikama: e, shiogama: e, shiroishi: e, tagajo: e, taiwa: e, tome: e, tomiya: e, wakuya: e, watari: e, yamamoto: e, zao: e }], miyazaki: [1, { aya: e, ebino: e, gokase: e, hyuga: e, kadogawa: e, kawaminami: e, kijo: e, kitagawa: e, kitakata: e, kitaura: e, kobayashi: e, kunitomi: e, kushima: e, mimata: e, miyakonojo: e, miyazaki: e, morotsuka: e, nichinan: e, nishimera: e, nobeoka: e, saito: e, shiiba: e, shintomi: e, takaharu: e, takanabe: e, takazaki: e, tsuno: e }], nagano: [1, { achi: e, agematsu: e, anan: e, aoki: e, asahi: e, azumino: e, chikuhoku: e, chikuma: e, chino: e, fujimi: e, hakuba: e, hara: e, hiraya: e, iida: e, iijima: e, iiyama: e, iizuna: e, ikeda: e, ikusaka: e, ina: e, karuizawa: e, kawakami: e, kiso: e, kisofukushima: e, kitaaiki: e, komagane: e, komoro: e, matsukawa: e, matsumoto: e, miasa: e, minamiaiki: e, minamimaki: e, minamiminowa: e, minowa: e, miyada: e, miyota: e, mochizuki: e, nagano: e, nagawa: e, nagiso: e, nakagawa: e, nakano: e, nozawaonsen: e, obuse: e, ogawa: e, okaya: e, omachi: e, omi: e, ookuwa: e, ooshika: e, otaki: e, otari: e, sakae: e, sakaki: e, saku: e, sakuho: e, shimosuwa: e, shinanomachi: e, shiojiri: e, suwa: e, suzaka: e, takagi: e, takamori: e, takayama: e, tateshina: e, tatsuno: e, togakushi: e, togura: e, tomi: e, ueda: e, wada: e, yamagata: e, yamanouchi: e, yasaka: e, yasuoka: e }], nagasaki: [1, { chijiwa: e, futsu: e, goto: e, hasami: e, hirado: e, iki: e, isahaya: e, kawatana: e, kuchinotsu: e, matsuura: e, nagasaki: e, obama: e, omura: e, oseto: e, saikai: e, sasebo: e, seihi: e, shimabara: e, shinkamigoto: e, togitsu: e, tsushima: e, unzen: e }], nara: [1, { ando: e, gose: e, heguri: e, higashiyoshino: e, ikaruga: e, ikoma: e, kamikitayama: e, kanmaki: e, kashiba: e, kashihara: e, katsuragi: e, kawai: e, kawakami: e, kawanishi: e, koryo: e, kurotaki: e, mitsue: e, miyake: e, nara: e, nosegawa: e, oji: e, ouda: e, oyodo: e, sakurai: e, sango: e, shimoichi: e, shimokitayama: e, shinjo: e, soni: e, takatori: e, tawaramoto: e, tenkawa: e, tenri: e, uda: e, yamatokoriyama: e, yamatotakada: e, yamazoe: e, yoshino: e }], niigata: [1, { aga: e, agano: e, gosen: e, itoigawa: e, izumozaki: e, joetsu: e, kamo: e, kariwa: e, kashiwazaki: e, minamiuonuma: e, mitsuke: e, muika: e, murakami: e, myoko: e, nagaoka: e, niigata: e, ojiya: e, omi: e, sado: e, sanjo: e, seiro: e, seirou: e, sekikawa: e, shibata: e, tagami: e, tainai: e, tochio: e, tokamachi: e, tsubame: e, tsunan: e, uonuma: e, yahiko: e, yoita: e, yuzawa: e }], oita: [1, { beppu: e, bungoono: e, bungotakada: e, hasama: e, hiji: e, himeshima: e, hita: e, kamitsue: e, kokonoe: e, kuju: e, kunisaki: e, kusu: e, oita: e, saiki: e, taketa: e, tsukumi: e, usa: e, usuki: e, yufu: e }], okayama: [1, { akaiwa: e, asakuchi: e, bizen: e, hayashima: e, ibara: e, kagamino: e, kasaoka: e, kibichuo: e, kumenan: e, kurashiki: e, maniwa: e, misaki: e, nagi: e, niimi: e, nishiawakura: e, okayama: e, satosho: e, setouchi: e, shinjo: e, shoo: e, soja: e, takahashi: e, tamano: e, tsuyama: e, wake: e, yakage: e }], okinawa: [1, { aguni: e, ginowan: e, ginoza: e, gushikami: e, haebaru: e, higashi: e, hirara: e, iheya: e, ishigaki: e, ishikawa: e, itoman: e, izena: e, kadena: e, kin: e, kitadaito: e, kitanakagusuku: e, kumejima: e, kunigami: e, minamidaito: e, motobu: e, nago: e, naha: e, nakagusuku: e, nakijin: e, nanjo: e, nishihara: e, ogimi: e, okinawa: e, onna: e, shimoji: e, taketomi: e, tarama: e, tokashiki: e, tomigusuku: e, tonaki: e, urasoe: e, uruma: e, yaese: e, yomitan: e, yonabaru: e, yonaguni: e, zamami: e }], osaka: [1, { abeno: e, chihayaakasaka: e, chuo: e, daito: e, fujiidera: e, habikino: e, hannan: e, higashiosaka: e, higashisumiyoshi: e, higashiyodogawa: e, hirakata: e, ibaraki: e, ikeda: e, izumi: e, izumiotsu: e, izumisano: e, kadoma: e, kaizuka: e, kanan: e, kashiwara: e, katano: e, kawachinagano: e, kishiwada: e, kita: e, kumatori: e, matsubara: e, minato: e, minoh: e, misaki: e, moriguchi: e, neyagawa: e, nishi: e, nose: e, osakasayama: e, sakai: e, sayama: e, sennan: e, settsu: e, shijonawate: e, shimamoto: e, suita: e, tadaoka: e, taishi: e, tajiri: e, takaishi: e, takatsuki: e, tondabayashi: e, toyonaka: e, toyono: e, yao: e }], saga: [1, { ariake: e, arita: e, fukudomi: e, genkai: e, hamatama: e, hizen: e, imari: e, kamimine: e, kanzaki: e, karatsu: e, kashima: e, kitagata: e, kitahata: e, kiyama: e, kouhoku: e, kyuragi: e, nishiarita: e, ogi: e, omachi: e, ouchi: e, saga: e, shiroishi: e, taku: e, tara: e, tosu: e, yoshinogari: e }], saitama: [1, { arakawa: e, asaka: e, chichibu: e, fujimi: e, fujimino: e, fukaya: e, hanno: e, hanyu: e, hasuda: e, hatogaya: e, hatoyama: e, hidaka: e, higashichichibu: e, higashimatsuyama: e, honjo: e, ina: e, iruma: e, iwatsuki: e, kamiizumi: e, kamikawa: e, kamisato: e, kasukabe: e, kawagoe: e, kawaguchi: e, kawajima: e, kazo: e, kitamoto: e, koshigaya: e, kounosu: e, kuki: e, kumagaya: e, matsubushi: e, minano: e, misato: e, miyashiro: e, miyoshi: e, moroyama: e, nagatoro: e, namegawa: e, niiza: e, ogano: e, ogawa: e, ogose: e, okegawa: e, omiya: e, otaki: e, ranzan: e, ryokami: e, saitama: e, sakado: e, satte: e, sayama: e, shiki: e, shiraoka: e, soka: e, sugito: e, toda: e, tokigawa: e, tokorozawa: e, tsurugashima: e, urawa: e, warabi: e, yashio: e, yokoze: e, yono: e, yorii: e, yoshida: e, yoshikawa: e, yoshimi: e }], shiga: [1, { aisho: e, gamo: e, higashiomi: e, hikone: e, koka: e, konan: e, kosei: e, koto: e, kusatsu: e, maibara: e, moriyama: e, nagahama: e, nishiazai: e, notogawa: e, omihachiman: e, otsu: e, ritto: e, ryuoh: e, takashima: e, takatsuki: e, torahime: e, toyosato: e, yasu: e }], shimane: [1, { akagi: e, ama: e, gotsu: e, hamada: e, higashiizumo: e, hikawa: e, hikimi: e, izumo: e, kakinoki: e, masuda: e, matsue: e, misato: e, nishinoshima: e, ohda: e, okinoshima: e, okuizumo: e, shimane: e, tamayu: e, tsuwano: e, unnan: e, yakumo: e, yasugi: e, yatsuka: e }], shizuoka: [1, { arai: e, atami: e, fuji: e, fujieda: e, fujikawa: e, fujinomiya: e, fukuroi: e, gotemba: e, haibara: e, hamamatsu: e, higashiizu: e, ito: e, iwata: e, izu: e, izunokuni: e, kakegawa: e, kannami: e, kawanehon: e, kawazu: e, kikugawa: e, kosai: e, makinohara: e, matsuzaki: e, minamiizu: e, mishima: e, morimachi: e, nishiizu: e, numazu: e, omaezaki: e, shimada: e, shimizu: e, shimoda: e, shizuoka: e, susono: e, yaizu: e, yoshida: e }], tochigi: [1, { ashikaga: e, bato: e, haga: e, ichikai: e, iwafune: e, kaminokawa: e, kanuma: e, karasuyama: e, kuroiso: e, mashiko: e, mibu: e, moka: e, motegi: e, nasu: e, nasushiobara: e, nikko: e, nishikata: e, nogi: e, ohira: e, ohtawara: e, oyama: e, sakura: e, sano: e, shimotsuke: e, shioya: e, takanezawa: e, tochigi: e, tsuga: e, ujiie: e, utsunomiya: e, yaita: e }], tokushima: [1, { aizumi: e, anan: e, ichiba: e, itano: e, kainan: e, komatsushima: e, matsushige: e, mima: e, minami: e, miyoshi: e, mugi: e, nakagawa: e, naruto: e, sanagochi: e, shishikui: e, tokushima: e, wajiki: e }], tokyo: [1, { adachi: e, akiruno: e, akishima: e, aogashima: e, arakawa: e, bunkyo: e, chiyoda: e, chofu: e, chuo: e, edogawa: e, fuchu: e, fussa: e, hachijo: e, hachioji: e, hamura: e, higashikurume: e, higashimurayama: e, higashiyamato: e, hino: e, hinode: e, hinohara: e, inagi: e, itabashi: e, katsushika: e, kita: e, kiyose: e, kodaira: e, koganei: e, kokubunji: e, komae: e, koto: e, kouzushima: e, kunitachi: e, machida: e, meguro: e, minato: e, mitaka: e, mizuho: e, musashimurayama: e, musashino: e, nakano: e, nerima: e, ogasawara: e, okutama: e, ome: e, oshima: e, ota: e, setagaya: e, shibuya: e, shinagawa: e, shinjuku: e, suginami: e, sumida: e, tachikawa: e, taito: e, tama: e, toshima: e }], tottori: [1, { chizu: e, hino: e, kawahara: e, koge: e, kotoura: e, misasa: e, nanbu: e, nichinan: e, sakaiminato: e, tottori: e, wakasa: e, yazu: e, yonago: e }], toyama: [1, { asahi: e, fuchu: e, fukumitsu: e, funahashi: e, himi: e, imizu: e, inami: e, johana: e, kamiichi: e, kurobe: e, nakaniikawa: e, namerikawa: e, nanto: e, nyuzen: e, oyabe: e, taira: e, takaoka: e, tateyama: e, toga: e, tonami: e, toyama: e, unazuki: e, uozu: e, yamada: e }], wakayama: [1, { arida: e, aridagawa: e, gobo: e, hashimoto: e, hidaka: e, hirogawa: e, inami: e, iwade: e, kainan: e, kamitonda: e, katsuragi: e, kimino: e, kinokawa: e, kitayama: e, koya: e, koza: e, kozagawa: e, kudoyama: e, kushimoto: e, mihama: e, misato: e, nachikatsuura: e, shingu: e, shirahama: e, taiji: e, tanabe: e, wakayama: e, yuasa: e, yura: e }], yamagata: [1, { asahi: e, funagata: e, higashine: e, iide: e, kahoku: e, kaminoyama: e, kaneyama: e, kawanishi: e, mamurogawa: e, mikawa: e, murayama: e, nagai: e, nakayama: e, nanyo: e, nishikawa: e, obanazawa: e, oe: e, oguni: e, ohkura: e, oishida: e, sagae: e, sakata: e, sakegawa: e, shinjo: e, shirataka: e, shonai: e, takahata: e, tendo: e, tozawa: e, tsuruoka: e, yamagata: e, yamanobe: e, yonezawa: e, yuza: e }], yamaguchi: [1, { abu: e, hagi: e, hikari: e, hofu: e, iwakuni: e, kudamatsu: e, mitou: e, nagato: e, oshima: e, shimonoseki: e, shunan: e, tabuse: e, tokuyama: e, toyota: e, ube: e, yuu: e }], yamanashi: [1, { chuo: e, doshi: e, fuefuki: e, fujikawa: e, fujikawaguchiko: e, fujiyoshida: e, hayakawa: e, hokuto: e, ichikawamisato: e, kai: e, kofu: e, koshu: e, kosuge: e, "minami-alps": e, minobu: e, nakamichi: e, nanbu: e, narusawa: e, nirasaki: e, nishikatsura: e, oshino: e, otsuki: e, showa: e, tabayama: e, tsuru: e, uenohara: e, yamanakako: e, yamanashi: e }], "xn--ehqz56n": e, 三重: e, "xn--1lqs03n": e, 京都: e, "xn--qqqt11m": e, 佐賀: e, "xn--f6qx53a": e, 兵庫: e, "xn--djrs72d6uy": e, 北海道: e, "xn--mkru45i": e, 千葉: e, "xn--0trq7p7nn": e, 和歌山: e, "xn--5js045d": e, 埼玉: e, "xn--kbrq7o": e, 大分: e, "xn--pssu33l": e, 大阪: e, "xn--ntsq17g": e, 奈良: e, "xn--uisz3g": e, 宮城: e, "xn--6btw5a": e, 宮崎: e, "xn--1ctwo": e, 富山: e, "xn--6orx2r": e, 山口: e, "xn--rht61e": e, 山形: e, "xn--rht27z": e, 山梨: e, "xn--nit225k": e, 岐阜: e, "xn--rht3d": e, 岡山: e, "xn--djty4k": e, 岩手: e, "xn--klty5x": e, 島根: e, "xn--kltx9a": e, 広島: e, "xn--kltp7d": e, 徳島: e, "xn--c3s14m": e, 愛媛: e, "xn--vgu402c": e, 愛知: e, "xn--efvn9s": e, 新潟: e, "xn--1lqs71d": e, 東京: e, "xn--4pvxs": e, 栃木: e, "xn--uuwu58a": e, 沖縄: e, "xn--zbx025d": e, 滋賀: e, "xn--8pvr4u": e, 熊本: e, "xn--5rtp49c": e, 石川: e, "xn--ntso0iqx3a": e, 神奈川: e, "xn--elqq16h": e, 福井: e, "xn--4it168d": e, 福岡: e, "xn--klt787d": e, 福島: e, "xn--rny31h": e, 秋田: e, "xn--7t0a264c": e, 群馬: e, "xn--uist22h": e, 茨城: e, "xn--8ltr62k": e, 長崎: e, "xn--2m4a15e": e, 長野: e, "xn--32vp30h": e, 青森: e, "xn--4it797k": e, 静岡: e, "xn--5rtq34k": e, 香川: e, "xn--k7yn95e": e, 高知: e, "xn--tor131o": e, 鳥取: e, "xn--d5qv7z876c": e, 鹿児島: e, kawasaki: w, kitakyushu: w, kobe: w, nagoya: w, sapporo: w, sendai: w, yokohama: w, buyshop: t, fashionstore: t, handcrafted: t, kawaiishop: t, supersale: t, theshop: t, "0am": t, "0g0": t, "0j0": t, "0t0": t, mydns: t, pgw: t, wjg: t, usercontent: t, angry: t, babyblue: t, babymilk: t, backdrop: t, bambina: t, bitter: t, blush: t, boo: t, boy: t, boyfriend: t, but: t, candypop: t, capoo: t, catfood: t, cheap: t, chicappa: t, chillout: t, chips: t, chowder: t, chu: t, ciao: t, cocotte: t, coolblog: t, cranky: t, cutegirl: t, daa: t, deca: t, deci: t, digick: t, egoism: t, fakefur: t, fem: t, flier: t, floppy: t, fool: t, frenchkiss: t, girlfriend: t, girly: t, gloomy: t, gonna: t, greater: t, hacca: t, heavy: t, her: t, hiho: t, hippy: t, holy: t, hungry: t, icurus: t, itigo: t, jellybean: t, kikirara: t, kill: t, kilo: t, kuron: t, littlestar: t, lolipopmc: t, lolitapunk: t, lomo: t, lovepop: t, lovesick: t, main: t, mods: t, mond: t, mongolian: t, moo: t, namaste: t, nikita: t, nobushi: t, noor: t, oops: t, parallel: t, parasite: t, pecori: t, peewee: t, penne: t, pepper: t, perma: t, pigboat: t, pinoko: t, punyu: t, pupu: t, pussycat: t, pya: t, raindrop: t, readymade: t, sadist: t, schoolbus: t, secret: t, staba: t, stripper: t, sub: t, sunnyday: t, thick: t, tonkotsu: t, under: t, upper: t, velvet: t, verse: t, versus: t, vivian: t, watson: t, weblike: t, whitesnow: t, zombie: t, hateblo: t, hatenablog: t, hatenadiary: t, "2-d": t, bona: t, crap: t, daynight: t, eek: t, flop: t, halfmoon: t, jeez: t, matrix: t, mimoza: t, netgamers: t, nyanta: t, o0o0: t, rdy: t, rgr: t, rulez: t, sakurastorage: [0, { isk01: $e, isk02: $e }], saloon: t, sblo: t, skr: t, tank: t, "uh-oh": t, undo: t, webaccel: [0, { rs: t, user: t }], websozai: t, xii: t }], ke: [1, { ac: e, co: e, go: e, info: e, me: e, mobi: e, ne: e, or: e, sc: e }], kg: [1, { com: e, edu: e, gov: e, mil: e, net: e, org: e, us: t, xx: t, ae: t }], kh: w, ki: At, km: [1, { ass: e, com: e, edu: e, gov: e, mil: e, nom: e, org: e, prd: e, tm: e, asso: e, coop: e, gouv: e, medecin: e, notaires: e, pharmaciens: e, presse: e, veterinaire: e }], kn: [1, { edu: e, gov: e, net: e, org: e }], kp: [1, { com: e, edu: e, gov: e, org: e, rep: e, tra: e }], kr: [1, { ac: e, ai: e, co: e, es: e, go: e, hs: e, io: e, it: e, kg: e, me: e, mil: e, ms: e, ne: e, or: e, pe: e, re: e, sc: e, busan: e, chungbuk: e, chungnam: e, daegu: e, daejeon: e, gangwon: e, gwangju: e, gyeongbuk: e, gyeonggi: e, gyeongnam: e, incheon: e, jeju: e, jeonbuk: e, jeonnam: e, seoul: e, ulsan: e, c01: t, "eliv-api": t, "eliv-cdn": t, "eliv-dns": t, mmv: t, vki: t }], kw: [1, { com: e, edu: e, emb: e, gov: e, ind: e, net: e, org: e }], ky: Te, kz: [1, { com: e, edu: e, gov: e, mil: e, net: e, org: e, jcloud: t }], la: [1, { com: e, edu: e, gov: e, info: e, int: e, net: e, org: e, per: e, bnr: t }], lb: n, lc: [1, { co: e, com: e, edu: e, gov: e, net: e, org: e, oy: t }], li: e, lk: [1, { ac: e, assn: e, com: e, edu: e, gov: e, grp: e, hotel: e, int: e, ltd: e, net: e, ngo: e, org: e, sch: e, soc: e, web: e }], lr: n, ls: [1, { ac: e, biz: e, co: e, edu: e, gov: e, info: e, net: e, org: e, sc: e }], lt: l, lu: [1, { "123website": t }], lv: [1, { asn: e, com: e, conf: e, edu: e, gov: e, id: e, mil: e, net: e, org: e }], ly: [1, { com: e, edu: e, gov: e, id: e, med: e, net: e, org: e, plc: e, sch: e }], ma: [1, { ac: e, co: e, gov: e, net: e, org: e, press: e }], mc: [1, { asso: e, tm: e }], md: [1, { ir: t }], me: [1, { ac: e, co: e, edu: e, gov: e, its: e, net: e, org: e, priv: e, c66: t, craft: t, edgestack: t, filegear: t, "filegear-sg": t, lohmus: t, barsy: t, mcdir: t, brasilia: t, ddns: t, dnsfor: t, hopto: t, loginto: t, noip: t, webhop: t, soundcast: t, tcp4: t, vp4: t, diskstation: t, dscloud: t, i234: t, myds: t, synology: t, transip: Ce, nohost: t }], mg: [1, { co: e, com: e, edu: e, gov: e, mil: e, nom: e, org: e, prd: e }], mh: e, mil: e, mk: [1, { com: e, edu: e, gov: e, inf: e, name: e, net: e, org: e }], ml: [1, { ac: e, art: e, asso: e, com: e, edu: e, gouv: e, gov: e, info: e, inst: e, net: e, org: e, pr: e, presse: e }], mm: w, mn: [1, { edu: e, gov: e, org: e, nyc: t }], mo: n, mobi: [1, { barsy: t, dscloud: t }], mp: [1, { ju: t }], mq: e, mr: l, ms: [1, { com: e, edu: e, gov: e, net: e, org: e, minisite: t }], mt: Te, mu: [1, { ac: e, co: e, com: e, gov: e, net: e, or: e, org: e }], museum: e, mv: [1, { aero: e, biz: e, com: e, coop: e, edu: e, gov: e, info: e, int: e, mil: e, museum: e, name: e, net: e, org: e, pro: e }], mw: [1, { ac: e, biz: e, co: e, com: e, coop: e, edu: e, gov: e, int: e, net: e, org: e }], mx: [1, { com: e, edu: e, gob: e, net: e, org: e }], my: [1, { biz: e, com: e, edu: e, gov: e, mil: e, name: e, net: e, org: e }], mz: [1, { ac: e, adv: e, co: e, edu: e, gov: e, mil: e, net: e, org: e }], na: [1, { alt: e, co: e, com: e, gov: e, net: e, org: e }], name: [1, { her: z, his: z, ispmanager: t, keenetic: t }], nc: [1, { asso: e, nom: e }], ne: e, net: [1, { adobeaemcloud: t, "adobeio-static": t, adobeioruntime: t, akadns: t, akamai: t, "akamai-staging": t, akamaiedge: t, "akamaiedge-staging": t, akamaihd: t, "akamaihd-staging": t, akamaiorigin: t, "akamaiorigin-staging": t, akamaized: t, "akamaized-staging": t, edgekey: t, "edgekey-staging": t, edgesuite: t, "edgesuite-staging": t, alwaysdata: t, myamaze: t, cloudfront: t, appudo: t, "atlassian-dev": [0, { prod: ge }], myfritz: t, shopselect: t, blackbaudcdn: t, boomla: t, bplaced: t, square7: t, cdn77: [0, { r: t }], "cdn77-ssl": t, gb: t, hu: t, jp: t, se: t, uk: t, clickrising: t, "ddns-ip": t, "dns-cloud": t, "dns-dynamic": t, cloudaccess: t, cloudflare: [2, { cdn: t }], cloudflareanycast: ge, cloudflarecn: ge, cloudflareglobal: ge, ctfcloud: t, "feste-ip": t, "knx-server": t, "static-access": t, cryptonomic: a, dattolocal: t, mydatto: t, debian: t, definima: t, deno: t, icp: a, de5: t, "at-band-camp": t, blogdns: t, "broke-it": t, buyshouses: t, dnsalias: t, dnsdojo: t, "does-it": t, dontexist: t, dynalias: t, dynathome: t, endofinternet: t, "from-az": t, "from-co": t, "from-la": t, "from-ny": t, "gets-it": t, "ham-radio-op": t, homeftp: t, homeip: t, homelinux: t, homeunix: t, "in-the-band": t, "is-a-chef": t, "is-a-geek": t, "isa-geek": t, "kicks-ass": t, "office-on-the": t, podzone: t, "scrapper-site": t, selfip: t, "sells-it": t, servebbs: t, serveftp: t, thruhere: t, webhop: t, casacam: t, dynu: t, dynuddns: t, mysynology: t, opik: t, spryt: t, dynv6: t, twmail: t, ru: t, channelsdvr: [2, { u: t }], fastly: [0, { freetls: t, map: t, prod: [0, { a: t, global: t }], ssl: [0, { a: t, b: t, global: t }] }], fastlylb: [2, { map: t }], "keyword-on": t, "live-on": t, "server-on": t, "cdn-edges": t, heteml: t, cloudfunctions: t, "grafana-dev": t, iobb: t, moonscale: t, "in-dsl": t, "in-vpn": t, oninferno: t, botdash: t, "apps-1and1": t, ipifony: t, cloudjiffy: [2, { "fra1-de": t, "west1-us": t }], elastx: [0, { "jls-sto1": t, "jls-sto2": t, "jls-sto3": t }], massivegrid: [0, { paas: [0, { "fr-1": t, "lon-1": t, "lon-2": t, "ny-1": t, "ny-2": t, "sg-1": t }] }], saveincloud: [0, { jelastic: t, "nordeste-idc": t }], scaleforce: fe, kinghost: t, uni5: t, krellian: t, ggff: t, localto: a, barsy: t, luyani: t, memset: t, "azure-api": t, "azure-mobile": t, azureedge: t, azurefd: t, azurestaticapps: [2, { 1: t, 2: t, 3: t, 4: t, 5: t, 6: t, 7: t, centralus: t, eastasia: t, eastus2: t, westeurope: t, westus2: t }], azurewebsites: t, cloudapp: t, trafficmanager: t, usgovcloudapi: ta, usgovcloudapp: t, usgovtrafficmanager: t, windows: ta, mynetname: [0, { sn: t }], routingthecloud: t, bounceme: t, ddns: t, "eating-organic": t, mydissent: t, myeffect: t, mymediapc: t, mypsx: t, mysecuritycamera: t, nhlfan: t, "no-ip": t, pgafan: t, privatizehealthinsurance: t, redirectme: t, serveblog: t, serveminecraft: t, sytes: t, dnsup: t, hicam: t, "now-dns": t, ownip: t, vpndns: t, cloudycluster: t, ovh: [0, { hosting: a, webpaas: a }], rackmaze: t, myradweb: t, in: t, "subsc-pay": t, squares: t, schokokeks: t, "firewall-gateway": t, seidat: t, senseering: t, siteleaf: t, mafelo: t, myspreadshop: t, "vps-host": [2, { jelastic: [0, { atl: t, njs: t, ric: t }] }], srcf: [0, { soc: t, user: t }], supabase: t, dsmynas: t, familyds: t, ts: [2, { c: a }], torproject: [2, { pages: t }], tunnelmole: t, vusercontent: t, "reserve-online": t, localcert: t, "community-pro": t, meinforum: t, yandexcloud: [2, { storage: t, website: t }], za: t, zabc: t }], nf: [1, { arts: e, com: e, firm: e, info: e, net: e, other: e, per: e, rec: e, store: e, web: e }], ng: [1, { com: e, edu: e, gov: e, i: e, mil: e, mobi: e, name: e, net: e, org: e, sch: e, biz: [2, { co: t, dl: t, go: t, lg: t, on: t }], col: t, firm: t, gen: t, ltd: t, ngo: t, plc: t }], ni: [1, { ac: e, biz: e, co: e, com: e, edu: e, gob: e, in: e, info: e, int: e, mil: e, net: e, nom: e, org: e, web: e }], nl: [1, { co: t, "hosting-cluster": t, gov: t, khplay: t, "123website": t, myspreadshop: t, transurl: a, cistron: t, demon: t }], no: [1, { fhs: e, folkebibl: e, fylkesbibl: e, idrett: e, museum: e, priv: e, vgs: e, dep: e, herad: e, kommune: e, mil: e, stat: e, aa: Z, ah: Z, bu: Z, fm: Z, hl: Z, hm: Z, "jan-mayen": Z, mr: Z, nl: Z, nt: Z, of: Z, ol: Z, oslo: Z, rl: Z, sf: Z, st: Z, svalbard: Z, tm: Z, tr: Z, va: Z, vf: Z, akrehamn: e, "xn--krehamn-dxa": e, åkrehamn: e, algard: e, "xn--lgrd-poac": e, ålgård: e, arna: e, bronnoysund: e, "xn--brnnysund-m8ac": e, brønnøysund: e, brumunddal: e, bryne: e, drobak: e, "xn--drbak-wua": e, drøbak: e, egersund: e, fetsund: e, floro: e, "xn--flor-jra": e, florø: e, fredrikstad: e, hokksund: e, honefoss: e, "xn--hnefoss-q1a": e, hønefoss: e, jessheim: e, jorpeland: e, "xn--jrpeland-54a": e, jørpeland: e, kirkenes: e, kopervik: e, krokstadelva: e, langevag: e, "xn--langevg-jxa": e, langevåg: e, leirvik: e, mjondalen: e, "xn--mjndalen-64a": e, mjøndalen: e, "mo-i-rana": e, mosjoen: e, "xn--mosjen-eya": e, mosjøen: e, nesoddtangen: e, orkanger: e, osoyro: e, "xn--osyro-wua": e, osøyro: e, raholt: e, "xn--rholt-mra": e, råholt: e, sandnessjoen: e, "xn--sandnessjen-ogb": e, sandnessjøen: e, skedsmokorset: e, slattum: e, spjelkavik: e, stathelle: e, stavern: e, stjordalshalsen: e, "xn--stjrdalshalsen-sqb": e, stjørdalshalsen: e, tananger: e, tranby: e, vossevangen: e, aarborte: e, aejrie: e, afjord: e, "xn--fjord-lra": e, åfjord: e, agdenes: e, akershus: na, aknoluokta: e, "xn--koluokta-7ya57h": e, ákŋoluokta: e, al: e, "xn--l-1fa": e, ål: e, alaheadju: e, "xn--laheadju-7ya": e, álaheadju: e, alesund: e, "xn--lesund-hua": e, ålesund: e, alstahaug: e, alta: e, "xn--lt-liac": e, áltá: e, alvdal: e, amli: e, "xn--mli-tla": e, åmli: e, amot: e, "xn--mot-tla": e, åmot: e, andasuolo: e, andebu: e, andoy: e, "xn--andy-ira": e, andøy: e, ardal: e, "xn--rdal-poa": e, årdal: e, aremark: e, arendal: e, "xn--s-1fa": e, ås: e, aseral: e, "xn--seral-lra": e, åseral: e, asker: e, askim: e, askoy: e, "xn--asky-ira": e, askøy: e, askvoll: e, asnes: e, "xn--snes-poa": e, åsnes: e, audnedaln: e, aukra: e, aure: e, aurland: e, "aurskog-holand": e, "xn--aurskog-hland-jnb": e, "aurskog-høland": e, austevoll: e, austrheim: e, averoy: e, "xn--avery-yua": e, averøy: e, badaddja: e, "xn--bdddj-mrabd": e, bådåddjå: e, "xn--brum-voa": e, bærum: e, bahcavuotna: e, "xn--bhcavuotna-s4a": e, báhcavuotna: e, bahccavuotna: e, "xn--bhccavuotna-k7a": e, báhccavuotna: e, baidar: e, "xn--bidr-5nac": e, báidár: e, bajddar: e, "xn--bjddar-pta": e, bájddar: e, balat: e, "xn--blt-elab": e, bálát: e, balestrand: e, ballangen: e, balsfjord: e, bamble: e, bardu: e, barum: e, batsfjord: e, "xn--btsfjord-9za": e, båtsfjord: e, bearalvahki: e, "xn--bearalvhki-y4a": e, bearalváhki: e, beardu: e, beiarn: e, berg: e, bergen: e, berlevag: e, "xn--berlevg-jxa": e, berlevåg: e, bievat: e, "xn--bievt-0qa": e, bievát: e, bindal: e, birkenes: e, bjerkreim: e, bjugn: e, bodo: e, "xn--bod-2na": e, bodø: e, bokn: e, bomlo: e, "xn--bmlo-gra": e, bømlo: e, bremanger: e, bronnoy: e, "xn--brnny-wuac": e, brønnøy: e, budejju: e, buskerud: na, bygland: e, bykle: e, cahcesuolo: e, "xn--hcesuolo-7ya35b": e, čáhcesuolo: e, davvenjarga: e, "xn--davvenjrga-y4a": e, davvenjárga: e, davvesiida: e, deatnu: e, dielddanuorri: e, divtasvuodna: e, divttasvuotna: e, donna: e, "xn--dnna-gra": e, dønna: e, dovre: e, drammen: e, drangedal: e, dyroy: e, "xn--dyry-ira": e, dyrøy: e, eid: e, eidfjord: e, eidsberg: e, eidskog: e, eidsvoll: e, eigersund: e, elverum: e, enebakk: e, engerdal: e, etne: e, etnedal: e, evenassi: e, "xn--eveni-0qa01ga": e, evenášši: e, evenes: e, "evje-og-hornnes": e, farsund: e, fauske: e, fedje: e, fet: e, finnoy: e, "xn--finny-yua": e, finnøy: e, fitjar: e, fjaler: e, fjell: e, fla: e, "xn--fl-zia": e, flå: e, flakstad: e, flatanger: e, flekkefjord: e, flesberg: e, flora: e, folldal: e, forde: e, "xn--frde-gra": e, førde: e, forsand: e, fosnes: e, "xn--frna-woa": e, fræna: e, frana: e, frei: e, frogn: e, froland: e, frosta: e, froya: e, "xn--frya-hra": e, frøya: e, fuoisku: e, fuossko: e, fusa: e, fyresdal: e, gaivuotna: e, "xn--givuotna-8ya": e, gáivuotna: e, galsa: e, "xn--gls-elac": e, gálsá: e, gamvik: e, gangaviika: e, "xn--ggaviika-8ya47h": e, gáŋgaviika: e, gaular: e, gausdal: e, giehtavuoatna: e, gildeskal: e, "xn--gildeskl-g0a": e, gildeskål: e, giske: e, gjemnes: e, gjerdrum: e, gjerstad: e, gjesdal: e, gjovik: e, "xn--gjvik-wua": e, gjøvik: e, gloppen: e, gol: e, gran: e, grane: e, granvin: e, gratangen: e, grimstad: e, grong: e, grue: e, gulen: e, guovdageaidnu: e, ha: e, "xn--h-2fa": e, hå: e, habmer: e, "xn--hbmer-xqa": e, hábmer: e, hadsel: e, "xn--hgebostad-g3a": e, hægebostad: e, hagebostad: e, halden: e, halsa: e, hamar: e, hamaroy: e, hammarfeasta: e, "xn--hmmrfeasta-s4ac": e, hámmárfeasta: e, hammerfest: e, hapmir: e, "xn--hpmir-xqa": e, hápmir: e, haram: e, hareid: e, harstad: e, hasvik: e, hattfjelldal: e, haugesund: e, hedmark: [0, { os: e, valer: e, "xn--vler-qoa": e, våler: e }], hemne: e, hemnes: e, hemsedal: e, hitra: e, hjartdal: e, hjelmeland: e, hobol: e, "xn--hobl-ira": e, hobøl: e, hof: e, hol: e, hole: e, holmestrand: e, holtalen: e, "xn--holtlen-hxa": e, holtålen: e, hordaland: [0, { os: e }], hornindal: e, horten: e, hoyanger: e, "xn--hyanger-q1a": e, høyanger: e, hoylandet: e, "xn--hylandet-54a": e, høylandet: e, hurdal: e, hurum: e, hvaler: e, hyllestad: e, ibestad: e, inderoy: e, "xn--indery-fya": e, inderøy: e, iveland: e, ivgu: e, jevnaker: e, jolster: e, "xn--jlster-bya": e, jølster: e, jondal: e, kafjord: e, "xn--kfjord-iua": e, kåfjord: e, karasjohka: e, "xn--krjohka-hwab49j": e, kárášjohka: e, karasjok: e, karlsoy: e, karmoy: e, "xn--karmy-yua": e, karmøy: e, kautokeino: e, klabu: e, "xn--klbu-woa": e, klæbu: e, klepp: e, kongsberg: e, kongsvinger: e, kraanghke: e, "xn--kranghke-b0a": e, kråanghke: e, kragero: e, "xn--krager-gya": e, kragerø: e, kristiansand: e, kristiansund: e, krodsherad: e, "xn--krdsherad-m8a": e, krødsherad: e, "xn--kvfjord-nxa": e, kvæfjord: e, "xn--kvnangen-k0a": e, kvænangen: e, kvafjord: e, kvalsund: e, kvam: e, kvanangen: e, kvinesdal: e, kvinnherad: e, kviteseid: e, kvitsoy: e, "xn--kvitsy-fya": e, kvitsøy: e, laakesvuemie: e, "xn--lrdal-sra": e, lærdal: e, lahppi: e, "xn--lhppi-xqa": e, láhppi: e, lardal: e, larvik: e, lavagis: e, lavangen: e, leangaviika: e, "xn--leagaviika-52b": e, leaŋgaviika: e, lebesby: e, leikanger: e, leirfjord: e, leka: e, leksvik: e, lenvik: e, lerdal: e, lesja: e, levanger: e, lier: e, lierne: e, lillehammer: e, lillesand: e, lindas: e, "xn--linds-pra": e, lindås: e, lindesnes: e, loabat: e, "xn--loabt-0qa": e, loabát: e, lodingen: e, "xn--ldingen-q1a": e, lødingen: e, lom: e, loppa: e, lorenskog: e, "xn--lrenskog-54a": e, lørenskog: e, loten: e, "xn--lten-gra": e, løten: e, lund: e, lunner: e, luroy: e, "xn--lury-ira": e, lurøy: e, luster: e, lyngdal: e, lyngen: e, malatvuopmi: e, "xn--mlatvuopmi-s4a": e, málatvuopmi: e, malselv: e, "xn--mlselv-iua": e, målselv: e, malvik: e, mandal: e, marker: e, marnardal: e, masfjorden: e, masoy: e, "xn--msy-ula0h": e, måsøy: e, "matta-varjjat": e, "xn--mtta-vrjjat-k7af": e, "mátta-várjjat": e, meland: e, meldal: e, melhus: e, meloy: e, "xn--mely-ira": e, meløy: e, meraker: e, "xn--merker-kua": e, meråker: e, midsund: e, "midtre-gauldal": e, moareke: e, "xn--moreke-jua": e, moåreke: e, modalen: e, modum: e, molde: e, "more-og-romsdal": [0, { heroy: e, sande: e }], "xn--mre-og-romsdal-qqb": [0, { "xn--hery-ira": e, sande: e }], "møre-og-romsdal": [0, { herøy: e, sande: e }], moskenes: e, moss: e, muosat: e, "xn--muost-0qa": e, muosát: e, naamesjevuemie: e, "xn--nmesjevuemie-tcba": e, nååmesjevuemie: e, "xn--nry-yla5g": e, nærøy: e, namdalseid: e, namsos: e, namsskogan: e, nannestad: e, naroy: e, narviika: e, narvik: e, naustdal: e, navuotna: e, "xn--nvuotna-hwa": e, návuotna: e, "nedre-eiker": e, nesna: e, nesodden: e, nesseby: e, nesset: e, nissedal: e, nittedal: e, "nord-aurdal": e, "nord-fron": e, "nord-odal": e, norddal: e, nordkapp: e, nordland: [0, { bo: e, "xn--b-5ga": e, bø: e, heroy: e, "xn--hery-ira": e, herøy: e }], "nordre-land": e, nordreisa: e, "nore-og-uvdal": e, notodden: e, notteroy: e, "xn--nttery-byae": e, nøtterøy: e, odda: e, oksnes: e, "xn--ksnes-uua": e, øksnes: e, omasvuotna: e, oppdal: e, oppegard: e, "xn--oppegrd-ixa": e, oppegård: e, orkdal: e, orland: e, "xn--rland-uua": e, ørland: e, orskog: e, "xn--rskog-uua": e, ørskog: e, orsta: e, "xn--rsta-fra": e, ørsta: e, osen: e, osteroy: e, "xn--ostery-fya": e, osterøy: e, ostfold: [0, { valer: e }], "xn--stfold-9xa": [0, { "xn--vler-qoa": e }], østfold: [0, { våler: e }], "ostre-toten": e, "xn--stre-toten-zcb": e, "østre-toten": e, overhalla: e, "ovre-eiker": e, "xn--vre-eiker-k8a": e, "øvre-eiker": e, oyer: e, "xn--yer-zna": e, øyer: e, oygarden: e, "xn--ygarden-p1a": e, øygarden: e, "oystre-slidre": e, "xn--ystre-slidre-ujb": e, "øystre-slidre": e, porsanger: e, porsangu: e, "xn--porsgu-sta26f": e, porsáŋgu: e, porsgrunn: e, rade: e, "xn--rde-ula": e, råde: e, radoy: e, "xn--rady-ira": e, radøy: e, "xn--rlingen-mxa": e, rælingen: e, rahkkeravju: e, "xn--rhkkervju-01af": e, ráhkkerávju: e, raisa: e, "xn--risa-5na": e, ráisa: e, rakkestad: e, ralingen: e, rana: e, randaberg: e, rauma: e, rendalen: e, rennebu: e, rennesoy: e, "xn--rennesy-v1a": e, rennesøy: e, rindal: e, ringebu: e, ringerike: e, ringsaker: e, risor: e, "xn--risr-ira": e, risør: e, rissa: e, roan: e, rodoy: e, "xn--rdy-0nab": e, rødøy: e, rollag: e, romsa: e, romskog: e, "xn--rmskog-bya": e, rømskog: e, roros: e, "xn--rros-gra": e, røros: e, rost: e, "xn--rst-0na": e, røst: e, royken: e, "xn--ryken-vua": e, røyken: e, royrvik: e, "xn--ryrvik-bya": e, røyrvik: e, ruovat: e, rygge: e, salangen: e, salat: e, "xn--slat-5na": e, sálat: e, "xn--slt-elab": e, sálát: e, saltdal: e, samnanger: e, sandefjord: e, sandnes: e, sandoy: e, "xn--sandy-yua": e, sandøy: e, sarpsborg: e, sauda: e, sauherad: e, sel: e, selbu: e, selje: e, seljord: e, siellak: e, sigdal: e, siljan: e, sirdal: e, skanit: e, "xn--sknit-yqa": e, skánit: e, skanland: e, "xn--sknland-fxa": e, skånland: e, skaun: e, skedsmo: e, ski: e, skien: e, skierva: e, "xn--skierv-uta": e, skiervá: e, skiptvet: e, skjak: e, "xn--skjk-soa": e, skjåk: e, skjervoy: e, "xn--skjervy-v1a": e, skjervøy: e, skodje: e, smola: e, "xn--smla-hra": e, smøla: e, snaase: e, "xn--snase-nra": e, snåase: e, snasa: e, "xn--snsa-roa": e, snåsa: e, snillfjord: e, snoasa: e, sogndal: e, sogne: e, "xn--sgne-gra": e, søgne: e, sokndal: e, sola: e, solund: e, somna: e, "xn--smna-gra": e, sømna: e, "sondre-land": e, "xn--sndre-land-0cb": e, "søndre-land": e, songdalen: e, "sor-aurdal": e, "xn--sr-aurdal-l8a": e, "sør-aurdal": e, "sor-fron": e, "xn--sr-fron-q1a": e, "sør-fron": e, "sor-odal": e, "xn--sr-odal-q1a": e, "sør-odal": e, "sor-varanger": e, "xn--sr-varanger-ggb": e, "sør-varanger": e, sorfold: e, "xn--srfold-bya": e, sørfold: e, sorreisa: e, "xn--srreisa-q1a": e, sørreisa: e, sortland: e, sorum: e, "xn--srum-gra": e, sørum: e, spydeberg: e, stange: e, stavanger: e, steigen: e, steinkjer: e, stjordal: e, "xn--stjrdal-s1a": e, stjørdal: e, stokke: e, "stor-elvdal": e, stord: e, stordal: e, storfjord: e, strand: e, stranda: e, stryn: e, sula: e, suldal: e, sund: e, sunndal: e, surnadal: e, sveio: e, svelvik: e, sykkylven: e, tana: e, telemark: [0, { bo: e, "xn--b-5ga": e, bø: e }], time: e, tingvoll: e, tinn: e, tjeldsund: e, tjome: e, "xn--tjme-hra": e, tjøme: e, tokke: e, tolga: e, tonsberg: e, "xn--tnsberg-q1a": e, tønsberg: e, torsken: e, "xn--trna-woa": e, træna: e, trana: e, tranoy: e, "xn--trany-yua": e, tranøy: e, troandin: e, trogstad: e, "xn--trgstad-r1a": e, trøgstad: e, tromsa: e, tromso: e, "xn--troms-zua": e, tromsø: e, trondheim: e, trysil: e, tvedestrand: e, tydal: e, tynset: e, tysfjord: e, tysnes: e, "xn--tysvr-vra": e, tysvær: e, tysvar: e, ullensaker: e, ullensvang: e, ulvik: e, unjarga: e, "xn--unjrga-rta": e, unjárga: e, utsira: e, vaapste: e, vadso: e, "xn--vads-jra": e, vadsø: e, "xn--vry-yla5g": e, værøy: e, vaga: e, "xn--vg-yiab": e, vågå: e, vagan: e, "xn--vgan-qoa": e, vågan: e, vagsoy: e, "xn--vgsy-qoa0j": e, vågsøy: e, vaksdal: e, valle: e, vang: e, vanylven: e, vardo: e, "xn--vard-jra": e, vardø: e, varggat: e, "xn--vrggt-xqad": e, várggát: e, varoy: e, vefsn: e, vega: e, vegarshei: e, "xn--vegrshei-c0a": e, vegårshei: e, vennesla: e, verdal: e, verran: e, vestby: e, vestfold: [0, { sande: e }], vestnes: e, "vestre-slidre": e, "vestre-toten": e, vestvagoy: e, "xn--vestvgy-ixa6o": e, vestvågøy: e, vevelstad: e, vik: e, vikna: e, vindafjord: e, voagat: e, volda: e, voss: e, co: t, "123hjemmeside": t, myspreadshop: t }], np: w, nr: At, nu: [1, { merseine: t, mine: t, shacknet: t, enterprisecloud: t }], nz: [1, { ac: e, co: e, cri: e, geek: e, gen: e, govt: e, health: e, iwi: e, kiwi: e, maori: e, "xn--mori-qsa": e, māori: e, mil: e, net: e, org: e, parliament: e, school: e, cloudns: t }], om: [1, { co: e, com: e, edu: e, gov: e, med: e, museum: e, net: e, org: e, pro: e }], onion: e, org: [1, { altervista: t, pimienta: t, poivron: t, potager: t, sweetpepper: t, cdn77: [0, { c: t, rsc: t }], "cdn77-secure": [0, { origin: [0, { ssl: t }] }], ae: t, cloudns: t, "ip-dynamic": t, ddnss: t, dpdns: t, duckdns: t, tunk: t, blogdns: t, blogsite: t, boldlygoingnowhere: t, dnsalias: t, dnsdojo: t, doesntexist: t, dontexist: t, doomdns: t, dvrdns: t, dynalias: t, dyndns: [2, { go: t, home: t }], endofinternet: t, endoftheinternet: t, "from-me": t, "game-host": t, gotdns: t, "hobby-site": t, homedns: t, homeftp: t, homelinux: t, homeunix: t, "is-a-bruinsfan": t, "is-a-candidate": t, "is-a-celticsfan": t, "is-a-chef": t, "is-a-geek": t, "is-a-knight": t, "is-a-linux-user": t, "is-a-patsfan": t, "is-a-soxfan": t, "is-found": t, "is-lost": t, "is-saved": t, "is-very-bad": t, "is-very-evil": t, "is-very-good": t, "is-very-nice": t, "is-very-sweet": t, "isa-geek": t, "kicks-ass": t, misconfused: t, podzone: t, readmyblog: t, selfip: t, sellsyourhome: t, servebbs: t, serveftp: t, servegame: t, "stuff-4-sale": t, webhop: t, accesscam: t, camdvr: t, freeddns: t, mywire: t, roxa: t, webredirect: t, twmail: t, eu: [2, { al: t, asso: t, at: t, au: t, be: t, bg: t, ca: t, cd: t, ch: t, cn: t, cy: t, cz: t, de: t, dk: t, edu: t, ee: t, es: t, fi: t, fr: t, gr: t, hr: t, hu: t, ie: t, il: t, in: t, int: t, is: t, it: t, jp: t, kr: t, lt: t, lu: t, lv: t, me: t, mk: t, mt: t, my: t, net: t, ng: t, nl: t, no: t, nz: t, pl: t, pt: t, ro: t, ru: t, se: t, si: t, sk: t, tr: t, uk: t, us: t }], fedorainfracloud: t, fedorapeople: t, fedoraproject: [0, { cloud: t, os: Rt, stg: [0, { os: Rt }] }], freedesktop: t, hatenadiary: t, hepforge: t, "in-dsl": t, "in-vpn": t, js: t, barsy: t, mayfirst: t, routingthecloud: t, bmoattachments: t, "cable-modem": t, collegefan: t, couchpotatofries: t, hopto: t, mlbfan: t, myftp: t, mysecuritycamera: t, nflfan: t, "no-ip": t, "read-books": t, ufcfan: t, zapto: t, dynserv: t, "now-dns": t, "is-local": t, httpbin: t, pubtls: t, jpn: t, "my-firewall": t, myfirewall: t, spdns: t, "small-web": t, dsmynas: t, familyds: t, teckids: $e, tuxfamily: t, hk: t, us: t, toolforge: t, wmcloud: [2, { beta: t }], wmflabs: t, za: t }], pa: [1, { abo: e, ac: e, com: e, edu: e, gob: e, ing: e, med: e, net: e, nom: e, org: e, sld: e }], pe: [1, { com: e, edu: e, gob: e, mil: e, net: e, nom: e, org: e }], pf: [1, { com: e, edu: e, org: e }], pg: w, ph: [1, { com: e, edu: e, gov: e, i: e, mil: e, net: e, ngo: e, org: e, cloudns: t }], pk: [1, { ac: e, biz: e, com: e, edu: e, fam: e, gkp: e, gob: e, gog: e, gok: e, gop: e, gos: e, gov: e, net: e, org: e, web: e }], pl: [1, { com: e, net: e, org: e, agro: e, aid: e, atm: e, auto: e, biz: e, edu: e, gmina: e, gsm: e, info: e, mail: e, media: e, miasta: e, mil: e, nieruchomosci: e, nom: e, pc: e, powiat: e, priv: e, realestate: e, rel: e, sex: e, shop: e, sklep: e, sos: e, szkola: e, targi: e, tm: e, tourism: e, travel: e, turystyka: e, gov: [1, { ap: e, griw: e, ic: e, is: e, kmpsp: e, konsulat: e, kppsp: e, kwp: e, kwpsp: e, mup: e, mw: e, oia: e, oirm: e, oke: e, oow: e, oschr: e, oum: e, pa: e, pinb: e, piw: e, po: e, pr: e, psp: e, psse: e, pup: e, rzgw: e, sa: e, sdn: e, sko: e, so: e, sr: e, starostwo: e, ug: e, ugim: e, um: e, umig: e, upow: e, uppo: e, us: e, uw: e, uzs: e, wif: e, wiih: e, winb: e, wios: e, witd: e, wiw: e, wkz: e, wsa: e, wskr: e, wsse: e, wuoz: e, wzmiuw: e, zp: e, zpisdn: e }], augustow: e, "babia-gora": e, bedzin: e, beskidy: e, bialowieza: e, bialystok: e, bielawa: e, bieszczady: e, boleslawiec: e, bydgoszcz: e, bytom: e, cieszyn: e, czeladz: e, czest: e, dlugoleka: e, elblag: e, elk: e, glogow: e, gniezno: e, gorlice: e, grajewo: e, ilawa: e, jaworzno: e, "jelenia-gora": e, jgora: e, kalisz: e, karpacz: e, kartuzy: e, kaszuby: e, katowice: e, "kazimierz-dolny": e, kepno: e, ketrzyn: e, klodzko: e, kobierzyce: e, kolobrzeg: e, konin: e, konskowola: e, kutno: e, lapy: e, lebork: e, legnica: e, lezajsk: e, limanowa: e, lomza: e, lowicz: e, lubin: e, lukow: e, malbork: e, malopolska: e, mazowsze: e, mazury: e, mielec: e, mielno: e, mragowo: e, naklo: e, nowaruda: e, nysa: e, olawa: e, olecko: e, olkusz: e, olsztyn: e, opoczno: e, opole: e, ostroda: e, ostroleka: e, ostrowiec: e, ostrowwlkp: e, pila: e, pisz: e, podhale: e, podlasie: e, polkowice: e, pomorskie: e, pomorze: e, prochowice: e, pruszkow: e, przeworsk: e, pulawy: e, radom: e, "rawa-maz": e, rybnik: e, rzeszow: e, sanok: e, sejny: e, skoczow: e, slask: e, slupsk: e, sosnowiec: e, "stalowa-wola": e, starachowice: e, stargard: e, suwalki: e, swidnica: e, swiebodzin: e, swinoujscie: e, szczecin: e, szczytno: e, tarnobrzeg: e, tgory: e, turek: e, tychy: e, ustka: e, walbrzych: e, warmia: e, warszawa: e, waw: e, wegrow: e, wielun: e, wlocl: e, wloclawek: e, wodzislaw: e, wolomin: e, wroclaw: e, zachpomor: e, zagan: e, zarow: e, zgora: e, zgorzelec: e, art: t, gliwice: t, krakow: t, poznan: t, wroc: t, zakopane: t, beep: t, "ecommerce-shop": t, cfolks: t, dfirma: t, dkonto: t, you2: t, shoparena: t, homesklep: t, sdscloud: t, unicloud: t, lodz: t, pabianice: t, plock: t, sieradz: t, skierniewice: t, zgierz: t, krasnik: t, leczna: t, lubartow: t, lublin: t, poniatowa: t, swidnik: t, co: t, torun: t, simplesite: t, myspreadshop: t, gda: t, gdansk: t, gdynia: t, med: t, sopot: t, bielsko: t }], pm: [1, { own: t, name: t }], pn: [1, { co: e, edu: e, gov: e, net: e, org: e }], post: e, pr: [1, { biz: e, com: e, edu: e, gov: e, info: e, isla: e, name: e, net: e, org: e, pro: e, ac: e, est: e, prof: e }], pro: [1, { aaa: e, aca: e, acct: e, avocat: e, bar: e, cpa: e, eng: e, jur: e, law: e, med: e, recht: e, cloudns: t, keenetic: t, barsy: t, ngrok: t }], ps: [1, { com: e, edu: e, gov: e, net: e, org: e, plo: e, sec: e }], pt: [1, { com: e, edu: e, gov: e, int: e, net: e, nome: e, org: e, publ: e, "123paginaweb": t }], pw: [1, { gov: e, cloudns: t, x443: t }], py: [1, { com: e, coop: e, edu: e, gov: e, mil: e, net: e, org: e }], qa: [1, { com: e, edu: e, gov: e, mil: e, name: e, net: e, org: e, sch: e }], re: [1, { asso: e, com: e, netlib: t, can: t }], ro: [1, { arts: e, com: e, firm: e, info: e, nom: e, nt: e, org: e, rec: e, store: e, tm: e, www: e, co: t, shop: t, barsy: t }], rs: [1, { ac: e, co: e, edu: e, gov: e, in: e, org: e, brendly: f, barsy: t, ox: t }], ru: [1, { ac: t, edu: t, gov: t, int: t, mil: t, eurodir: t, adygeya: t, bashkiria: t, bir: t, cbg: t, com: t, dagestan: t, grozny: t, kalmykia: t, kustanai: t, marine: t, mordovia: t, msk: t, mytis: t, nalchik: t, nov: t, pyatigorsk: t, spb: t, vladikavkaz: t, vladimir: t, na4u: t, mircloud: t, myjino: [2, { hosting: a, landing: a, spectrum: a, vps: a }], cldmail: [0, { hb: t }], mcdir: [2, { vps: t }], mcpre: t, net: t, org: t, pp: t, ras: t }], rw: [1, { ac: e, co: e, coop: e, gov: e, mil: e, net: e, org: e }], sa: [1, { com: e, edu: e, gov: e, med: e, net: e, org: e, pub: e, sch: e }], sb: n, sc: n, sd: [1, { com: e, edu: e, gov: e, info: e, med: e, net: e, org: e, tv: e }], se: [1, { a: e, ac: e, b: e, bd: e, brand: e, c: e, d: e, e, f: e, fh: e, fhsk: e, fhv: e, g: e, h: e, i: e, k: e, komforb: e, kommunalforbund: e, komvux: e, l: e, lanbib: e, m: e, n: e, naturbruksgymn: e, o: e, org: e, p: e, parti: e, pp: e, press: e, r: e, s: e, t: e, tm: e, u: e, w: e, x: e, y: e, z: e, com: t, iopsys: t, "123minsida": t, itcouldbewor: t, myspreadshop: t }], sg: [1, { com: e, edu: e, gov: e, net: e, org: e, enscaled: t }], sh: [1, { com: e, gov: e, mil: e, net: e, org: e, hashbang: t, botda: t, lovable: t, platform: [0, { ent: t, eu: t, us: t }], teleport: t, now: t }], si: [1, { f5: t, gitapp: t, gitpage: t }], sj: e, sk: [1, { org: e }], sl: n, sm: e, sn: [1, { art: e, com: e, edu: e, gouv: e, org: e, univ: e }], so: [1, { com: e, edu: e, gov: e, me: e, net: e, org: e, surveys: t }], sr: e, ss: [1, { biz: e, co: e, com: e, edu: e, gov: e, me: e, net: e, org: e, sch: e }], st: [1, { co: e, com: e, consulado: e, edu: e, embaixada: e, mil: e, net: e, org: e, principe: e, saotome: e, store: e, helioho: t, cn: a, kirara: t, noho: t }], su: [1, { abkhazia: t, adygeya: t, aktyubinsk: t, arkhangelsk: t, armenia: t, ashgabad: t, azerbaijan: t, balashov: t, bashkiria: t, bryansk: t, bukhara: t, chimkent: t, dagestan: t, "east-kazakhstan": t, exnet: t, georgia: t, grozny: t, ivanovo: t, jambyl: t, kalmykia: t, kaluga: t, karacol: t, karaganda: t, karelia: t, khakassia: t, krasnodar: t, kurgan: t, kustanai: t, lenug: t, mangyshlak: t, mordovia: t, msk: t, murmansk: t, nalchik: t, navoi: t, "north-kazakhstan": t, nov: t, obninsk: t, penza: t, pokrovsk: t, sochi: t, spb: t, tashkent: t, termez: t, togliatti: t, troitsk: t, tselinograd: t, tula: t, tuva: t, vladikavkaz: t, vladimir: t, vologda: t }], sv: [1, { com: e, edu: e, gob: e, org: e, red: e }], sx: l, sy: r, sz: [1, { ac: e, co: e, org: e }], tc: e, td: e, tel: e, tf: [1, { sch: t }], tg: e, th: [1, { ac: e, co: e, go: e, in: e, mi: e, net: e, or: e, online: t, shop: t }], tj: [1, { ac: e, biz: e, co: e, com: e, edu: e, go: e, gov: e, int: e, mil: e, name: e, net: e, nic: e, org: e, test: e, web: e }], tk: e, tl: l, tm: [1, { co: e, com: e, edu: e, gov: e, mil: e, net: e, nom: e, org: e }], tn: [1, { com: e, ens: e, fin: e, gov: e, ind: e, info: e, intl: e, mincom: e, nat: e, net: e, org: e, perso: e, tourism: e, orangecloud: t }], to: [1, { 611: t, com: e, edu: e, gov: e, mil: e, net: e, org: e, oya: t, x0: t, quickconnect: q, vpnplus: t, nett: t }], tr: [1, { av: e, bbs: e, bel: e, biz: e, com: e, dr: e, edu: e, gen: e, gov: e, info: e, k12: e, kep: e, mil: e, name: e, net: e, org: e, pol: e, tel: e, tsk: e, tv: e, web: e, nc: l }], tt: [1, { biz: e, co: e, com: e, edu: e, gov: e, info: e, mil: e, name: e, net: e, org: e, pro: e }], tv: [1, { "better-than": t, dyndns: t, "on-the-web": t, "worse-than": t, from: t, sakura: t }], tw: [1, { club: e, com: [1, { mymailer: t }], ebiz: e, edu: e, game: e, gov: e, idv: e, mil: e, net: e, org: e, url: t, mydns: t }], tz: [1, { ac: e, co: e, go: e, hotel: e, info: e, me: e, mil: e, mobi: e, ne: e, or: e, sc: e, tv: e }], ua: [1, { com: e, edu: e, gov: e, in: e, net: e, org: e, cherkassy: e, cherkasy: e, chernigov: e, chernihiv: e, chernivtsi: e, chernovtsy: e, ck: e, cn: e, cr: e, crimea: e, cv: e, dn: e, dnepropetrovsk: e, dnipropetrovsk: e, donetsk: e, dp: e, if: e, "ivano-frankivsk": e, kh: e, kharkiv: e, kharkov: e, kherson: e, khmelnitskiy: e, khmelnytskyi: e, kiev: e, kirovograd: e, km: e, kr: e, kropyvnytskyi: e, krym: e, ks: e, kv: e, kyiv: e, lg: e, lt: e, lugansk: e, luhansk: e, lutsk: e, lv: e, lviv: e, mk: e, mykolaiv: e, nikolaev: e, od: e, odesa: e, odessa: e, pl: e, poltava: e, rivne: e, rovno: e, rv: e, sb: e, sebastopol: e, sevastopol: e, sm: e, sumy: e, te: e, ternopil: e, uz: e, uzhgorod: e, uzhhorod: e, vinnica: e, vinnytsia: e, vn: e, volyn: e, yalta: e, zakarpattia: e, zaporizhzhe: e, zaporizhzhia: e, zhitomir: e, zhytomyr: e, zp: e, zt: e, cc: t, inf: t, ltd: t, cx: t, biz: t, co: t, pp: t, v: t }], ug: [1, { ac: e, co: e, com: e, edu: e, go: e, gov: e, mil: e, ne: e, or: e, org: e, sc: e, us: e }], uk: [1, { ac: e, co: [1, { bytemark: [0, { dh: t, vm: t }], layershift: fe, barsy: t, barsyonline: t, retrosnub: U, "nh-serv": t, "no-ip": t, adimo: t, myspreadshop: t }], gov: [1, { api: t, campaign: t, service: t }], ltd: e, me: e, net: e, nhs: e, org: [1, { glug: t, lug: t, lugs: t, affinitylottery: t, raffleentry: t, weeklylottery: t }], plc: e, police: e, sch: w, conn: t, copro: t, hosp: t, "independent-commission": t, "independent-inquest": t, "independent-inquiry": t, "independent-panel": t, "independent-review": t, "public-inquiry": t, "royal-commission": t, pymnt: t, barsy: t, nimsite: t, oraclegovcloudapps: a }], us: [1, { dni: e, isa: e, nsn: e, ak: N, al: N, ar: N, as: N, az: N, ca: N, co: N, ct: N, dc: N, de: aa, fl: N, ga: N, gu: N, hi: qt, ia: N, id: N, il: N, in: N, ks: N, ky: N, la: N, ma: [1, { k12: [1, { chtr: e, paroch: e, pvt: e }], cc: e, lib: e }], md: N, me: N, mi: [1, { k12: e, cc: e, lib: e, "ann-arbor": e, cog: e, dst: e, eaton: e, gen: e, mus: e, tec: e, washtenaw: e }], mn: N, mo: N, ms: [1, { k12: e, cc: e }], mt: N, nc: N, nd: qt, ne: N, nh: N, nj: N, nm: N, nv: N, ny: N, oh: N, ok: N, or: N, pa: N, pr: N, ri: qt, sc: N, sd: qt, tn: N, tx: N, ut: N, va: N, vi: N, vt: N, wa: N, wi: N, wv: aa, wy: N, cloudns: t, "is-by": t, "land-4-sale": t, "stuff-4-sale": t, heliohost: t, enscaled: [0, { phx: t }], mircloud: t, "azure-api": t, azurewebsites: t, ngo: t, golffan: t, noip: t, pointto: t, freeddns: t, srv: [2, { gh: t, gl: t }], servername: t }], uy: [1, { com: e, edu: e, gub: e, mil: e, net: e, org: e, gv: t }], uz: [1, { co: e, com: e, net: e, org: e }], va: e, vc: [1, { com: e, edu: e, gov: e, mil: e, net: e, org: e, gv: [2, { d: t }], "0e": a, mydns: t }], ve: [1, { arts: e, bib: e, co: e, com: e, e12: e, edu: e, emprende: e, firm: e, gob: e, gov: e, ia: e, info: e, int: e, mil: e, net: e, nom: e, org: e, rar: e, rec: e, store: e, tec: e, web: e }], vg: [1, { edu: e }], vi: [1, { co: e, com: e, k12: e, net: e, org: e }], vn: [1, { ac: e, ai: e, biz: e, com: e, edu: e, gov: e, health: e, id: e, info: e, int: e, io: e, name: e, net: e, org: e, pro: e, angiang: e, bacgiang: e, backan: e, baclieu: e, bacninh: e, "baria-vungtau": e, bentre: e, binhdinh: e, binhduong: e, binhphuoc: e, binhthuan: e, camau: e, cantho: e, caobang: e, daklak: e, daknong: e, danang: e, dienbien: e, dongnai: e, dongthap: e, gialai: e, hagiang: e, haiduong: e, haiphong: e, hanam: e, hanoi: e, hatinh: e, haugiang: e, hoabinh: e, hungyen: e, khanhhoa: e, kiengiang: e, kontum: e, laichau: e, lamdong: e, langson: e, laocai: e, longan: e, namdinh: e, nghean: e, ninhbinh: e, ninhthuan: e, phutho: e, phuyen: e, quangbinh: e, quangnam: e, quangngai: e, quangninh: e, quangtri: e, soctrang: e, sonla: e, tayninh: e, thaibinh: e, thainguyen: e, thanhhoa: e, thanhphohochiminh: e, thuathienhue: e, tiengiang: e, travinh: e, tuyenquang: e, vinhlong: e, vinhphuc: e, yenbai: e }], vu: Te, wf: [1, { biz: t, sch: t }], ws: [1, { com: e, edu: e, gov: e, net: e, org: e, advisor: a, cloud66: t, dyndns: t, mypets: t }], yt: [1, { org: t }], "xn--mgbaam7a8h": e, امارات: e, "xn--y9a3aq": e, հայ: e, "xn--54b7fta0cc": e, বাংলা: e, "xn--90ae": e, бг: e, "xn--mgbcpq6gpa1a": e, البحرين: e, "xn--90ais": e, бел: e, "xn--fiqs8s": e, 中国: e, "xn--fiqz9s": e, 中國: e, "xn--lgbbat1ad8j": e, الجزائر: e, "xn--wgbh1c": e, مصر: e, "xn--e1a4c": e, ею: e, "xn--qxa6a": e, ευ: e, "xn--mgbah1a3hjkrd": e, موريتانيا: e, "xn--node": e, გე: e, "xn--qxam": e, ελ: e, "xn--j6w193g": [1, { "xn--gmqw5a": e, "xn--55qx5d": e, "xn--mxtq1m": e, "xn--wcvs22d": e, "xn--uc0atv": e, "xn--od0alg": e }], 香港: [1, { 個人: e, 公司: e, 政府: e, 教育: e, 組織: e, 網絡: e }], "xn--2scrj9c": e, ಭಾರತ: e, "xn--3hcrj9c": e, ଭାରତ: e, "xn--45br5cyl": e, ভাৰত: e, "xn--h2breg3eve": e, भारतम्: e, "xn--h2brj9c8c": e, भारोत: e, "xn--mgbgu82a": e, ڀارت: e, "xn--rvc1e0am3e": e, ഭാരതം: e, "xn--h2brj9c": e, भारत: e, "xn--mgbbh1a": e, بارت: e, "xn--mgbbh1a71e": e, بھارت: e, "xn--fpcrj9c3d": e, భారత్: e, "xn--gecrj9c": e, ભારત: e, "xn--s9brj9c": e, ਭਾਰਤ: e, "xn--45brj9c": e, ভারত: e, "xn--xkc2dl3a5ee0h": e, இந்தியா: e, "xn--mgba3a4f16a": e, ایران: e, "xn--mgba3a4fra": e, ايران: e, "xn--mgbtx2b": e, عراق: e, "xn--mgbayh7gpa": e, الاردن: e, "xn--3e0b707e": e, 한국: e, "xn--80ao21a": e, қаз: e, "xn--q7ce6a": e, ລາວ: e, "xn--fzc2c9e2c": e, ලංකා: e, "xn--xkc2al3hye2a": e, இலங்கை: e, "xn--mgbc0a9azcg": e, المغرب: e, "xn--d1alf": e, мкд: e, "xn--l1acc": e, мон: e, "xn--mix891f": e, 澳門: e, "xn--mix082f": e, 澳门: e, "xn--mgbx4cd0ab": e, مليسيا: e, "xn--mgb9awbf": e, عمان: e, "xn--mgbai9azgqp6j": e, پاکستان: e, "xn--mgbai9a5eva00b": e, پاكستان: e, "xn--ygbi2ammx": e, فلسطين: e, "xn--90a3ac": [1, { "xn--80au": e, "xn--90azh": e, "xn--d1at": e, "xn--c1avg": e, "xn--o1ac": e, "xn--o1ach": e }], срб: [1, { ак: e, обр: e, од: e, орг: e, пр: e, упр: e }], "xn--p1ai": e, рф: e, "xn--wgbl6a": e, قطر: e, "xn--mgberp4a5d4ar": e, السعودية: e, "xn--mgberp4a5d4a87g": e, السعودیة: e, "xn--mgbqly7c0a67fbc": e, السعودیۃ: e, "xn--mgbqly7cvafr": e, السعوديه: e, "xn--mgbpl2fh": e, سودان: e, "xn--yfro4i67o": e, 新加坡: e, "xn--clchc0ea0b2g2a9gcd": e, சிங்கப்பூர்: e, "xn--ogbpf8fl": e, سورية: e, "xn--mgbtf8fl": e, سوريا: e, "xn--o3cw4h": [1, { "xn--o3cyx2a": e, "xn--12co0c3b4eva": e, "xn--m3ch0j3a": e, "xn--h3cuzk1di": e, "xn--12c1fe0br": e, "xn--12cfi8ixb8l": e }], ไทย: [1, { ทหาร: e, ธุรกิจ: e, เน็ต: e, รัฐบาล: e, ศึกษา: e, องค์กร: e }], "xn--pgbs0dh": e, تونس: e, "xn--kpry57d": e, 台灣: e, "xn--kprw13d": e, 台湾: e, "xn--nnx388a": e, 臺灣: e, "xn--j1amh": e, укр: e, "xn--mgb2ddes": e, اليمن: e, xxx: e, ye: r, za: [0, { ac: e, agric: e, alt: e, co: e, edu: e, gov: e, grondar: e, law: e, mil: e, net: e, ngo: e, nic: e, nis: e, nom: e, org: e, school: e, tm: e, web: e }], zm: [1, { ac: e, biz: e, co: e, com: e, edu: e, gov: e, info: e, mil: e, net: e, org: e, sch: e }], zw: [1, { ac: e, co: e, gov: e, mil: e, org: e }], aaa: e, aarp: e, abb: e, abbott: e, abbvie: e, abc: e, able: e, abogado: e, abudhabi: e, academy: [1, { official: t }], accenture: e, accountant: e, accountants: e, aco: e, actor: e, ads: e, adult: e, aeg: e, aetna: e, afl: e, africa: e, agakhan: e, agency: e, aig: e, airbus: e, airforce: e, airtel: e, akdn: e, alibaba: e, alipay: e, allfinanz: e, allstate: e, ally: e, alsace: e, alstom: e, amazon: e, americanexpress: e, americanfamily: e, amex: e, amfam: e, amica: e, amsterdam: e, analytics: e, android: e, anquan: e, anz: e, aol: e, apartments: e, app: [1, { adaptable: t, aiven: t, beget: a, brave: i, clerk: t, clerkstage: t, cloudflare: t, wnext: t, csb: [2, { preview: t }], convex: t, corespeed: t, deta: t, ondigitalocean: t, easypanel: t, encr: [2, { frontend: t }], evervault: s, expo: [2, { staging: t }], edgecompute: t, "on-fleek": t, flutterflow: t, sprites: t, e2b: t, framer: t, gadget: t, github: t, hosted: a, run: [0, { "*": t, mtls: a }], web: t, hackclub: t, hasura: t, onhercules: t, botdash: t, shiptoday: t, leapcell: t, loginline: t, lovable: t, luyani: t, magicpatterns: t, medusajs: t, messerli: t, miren: t, mocha: t, netlify: t, ngrok: t, "ngrok-free": t, developer: a, noop: t, northflank: a, upsun: a, railway: [0, { up: t }], replit: o, nyat: t, snowflake: [0, { "*": t, privatelink: a }], streamlit: t, spawnbase: t, telebit: t, typedream: t, vercel: t, wal: t, wasmer: t, bookonline: t, windsurf: t, base44: t, zeabur: t, zerops: a }], apple: [1, { int: [2, { cloud: [0, { "*": t, r: [0, { "*": t, "ap-north-1": a, "ap-south-1": a, "ap-south-2": a, "eu-central-1": a, "eu-north-1": a, "us-central-1": a, "us-central-2": a, "us-east-1": a, "us-east-2": a, "us-west-1": a, "us-west-2": a, "us-west-3": a }] }] }] }], aquarelle: e, arab: e, aramco: e, archi: e, army: e, art: e, arte: e, asda: e, associates: e, athleta: e, attorney: e, auction: e, audi: e, audible: e, audio: e, auspost: e, author: e, auto: e, autos: e, aws: [1, { on: [0, { "af-south-1": c, "ap-east-1": c, "ap-northeast-1": c, "ap-northeast-2": c, "ap-northeast-3": c, "ap-south-1": c, "ap-south-2": u, "ap-southeast-1": c, "ap-southeast-2": c, "ap-southeast-3": c, "ap-southeast-4": u, "ap-southeast-5": u, "ca-central-1": c, "ca-west-1": u, "eu-central-1": c, "eu-central-2": u, "eu-north-1": c, "eu-south-1": c, "eu-south-2": u, "eu-west-1": c, "eu-west-2": c, "eu-west-3": c, "il-central-1": u, "me-central-1": u, "me-south-1": c, "sa-east-1": c, "us-east-1": c, "us-east-2": c, "us-west-1": c, "us-west-2": c, "ap-southeast-7": d, "mx-central-1": d, "us-gov-east-1": p, "us-gov-west-1": p }], sagemaker: [0, { "ap-northeast-1": h, "ap-northeast-2": h, "ap-south-1": h, "ap-southeast-1": h, "ap-southeast-2": h, "ca-central-1": v, "eu-central-1": h, "eu-west-1": h, "eu-west-2": h, "us-east-1": v, "us-east-2": v, "us-west-2": v, "af-south-1": m, "ap-east-1": m, "ap-northeast-3": m, "ap-south-2": g, "ap-southeast-3": m, "ap-southeast-4": g, "ca-west-1": [0, { notebook: t, "notebook-fips": t }], "eu-central-2": m, "eu-north-1": m, "eu-south-1": m, "eu-south-2": m, "eu-west-3": m, "il-central-1": m, "me-central-1": m, "me-south-1": m, "sa-east-1": m, "us-gov-east-1": k, "us-gov-west-1": k, "us-west-1": [0, { notebook: t, "notebook-fips": t, studio: t }], experiments: a }], repost: [0, { private: a }] }], axa: e, azure: e, baby: e, baidu: e, banamex: e, band: e, bank: e, bar: e, barcelona: e, barclaycard: e, barclays: e, barefoot: e, bargains: e, baseball: e, basketball: [1, { aus: t, nz: t }], bauhaus: e, bayern: e, bbc: e, bbt: e, bbva: e, bcg: e, bcn: e, beats: e, beauty: e, beer: e, berlin: e, best: e, bestbuy: e, bet: e, bharti: e, bible: e, bid: e, bike: e, bing: e, bingo: e, bio: e, black: e, blackfriday: e, blockbuster: e, blog: e, bloomberg: e, blue: e, bms: e, bmw: e, bnpparibas: e, boats: e, boehringer: e, bofa: e, bom: e, bond: e, boo: e, book: e, booking: e, bosch: e, bostik: e, boston: e, bot: e, boutique: e, box: e, bradesco: e, bridgestone: e, broadway: e, broker: e, brother: e, brussels: e, build: [1, { shiptoday: t, v0: t, windsurf: t }], builders: [1, { cloudsite: t }], business: E, buy: e, buzz: e, bzh: e, cab: e, cafe: e, cal: e, call: e, calvinklein: e, cam: e, camera: e, camp: [1, { emf: [0, { at: t }] }], canon: e, capetown: e, capital: e, capitalone: e, car: e, caravan: e, cards: e, care: e, career: e, careers: e, cars: e, casa: [1, { nabu: [0, { ui: t }] }], case: [1, { sav: t }], cash: e, casino: e, catering: e, catholic: e, cba: e, cbn: e, cbre: e, center: e, ceo: e, cern: e, cfa: e, cfd: e, chanel: e, channel: e, charity: e, chase: e, chat: e, cheap: e, chintai: e, christmas: e, chrome: e, church: e, cipriani: e, circle: e, cisco: e, citadel: e, citi: e, citic: e, city: e, claims: e, cleaning: e, click: e, clinic: e, clinique: e, clothing: e, cloud: [1, { antagonist: t, convex: t, elementor: t, emergent: t, encoway: [0, { eu: t }], statics: a, ravendb: t, axarnet: [0, { "es-1": t }], diadem: t, jelastic: [0, { vip: t }], jele: t, "jenv-aruba": [0, { aruba: [0, { eur: [0, { it1: t }] }], it1: t }], keliweb: [2, { cs: t }], oxa: [2, { tn: t, uk: t }], primetel: [2, { uk: t }], reclaim: [0, { ca: t, uk: t, us: t }], trendhosting: [0, { ch: t, de: t }], jote: t, jotelulu: t, kuleuven: t, laravel: t, linkyard: t, magentosite: a, matlab: t, observablehq: t, perspecta: t, vapor: t, "on-rancher": a, scw: [0, { baremetal: [0, { "fr-par-1": t, "fr-par-2": t, "nl-ams-1": t }], "fr-par": [0, { cockpit: t, ddl: t, dtwh: t, fnc: [2, { functions: t }], ifr: t, k8s: b, kafk: t, mgdb: t, rdb: t, s3: t, "s3-website": t, scbl: t, whm: t }], instances: [0, { priv: t, pub: t }], k8s: t, "nl-ams": [0, { cockpit: t, ddl: t, dtwh: t, ifr: t, k8s: b, kafk: t, mgdb: t, rdb: t, s3: t, "s3-website": t, scbl: t, whm: t }], "pl-waw": [0, { cockpit: t, ddl: t, dtwh: t, ifr: t, k8s: b, kafk: t, mgdb: t, rdb: t, s3: t, "s3-website": t, scbl: t }], scalebook: t, smartlabeling: t }], servebolt: t, onstackit: [0, { runs: t }], trafficplex: t, "unison-services": t, urown: t, voorloper: t, zap: t }], club: [1, { cloudns: t, jele: t, barsy: t }], clubmed: e, coach: e, codes: [1, { owo: a }], coffee: e, college: e, cologne: e, commbank: e, community: [1, { nog: t, ravendb: t, myforum: t }], company: e, compare: e, computer: e, comsec: e, condos: e, construction: e, consulting: e, contact: e, contractors: e, cooking: e, cool: [1, { elementor: t, de: t }], corsica: e, country: e, coupon: e, coupons: e, courses: e, cpa: e, credit: e, creditcard: e, creditunion: e, cricket: e, crown: e, crs: e, cruise: e, cruises: e, cuisinella: e, cymru: e, cyou: e, dad: e, dance: e, data: e, date: e, dating: e, datsun: e, day: e, dclk: e, dds: e, deal: e, dealer: e, deals: e, degree: e, delivery: e, dell: e, deloitte: e, delta: e, democrat: e, dental: e, dentist: e, desi: e, design: [1, { graphic: t, bss: t }], dev: [1, { myaddr: t, panel: t, bearblog: t, brave: i, lcl: a, lclstage: a, stg: a, stgstage: a, pages: t, r2: t, workers: t, deno: t, "deno-staging": t, deta: t, lp: [2, { api: t, objects: t }], evervault: s, fly: t, githubpreview: t, gateway: a, grebedoc: t, botdash: t, inbrowser: a, "is-a-good": t, iserv: t, leapcell: t, runcontainers: t, localcert: [0, { user: a }], loginline: t, barsy: t, mediatech: t, "mocha-sandbox": t, modx: t, ngrok: t, "ngrok-free": t, "is-a-fullstack": t, "is-cool": t, "is-not-a": t, localplayer: t, xmit: t, "platter-app": t, replit: [2, { archer: t, bones: t, canary: t, global: t, hacker: t, id: t, janeway: t, kim: t, kira: t, kirk: t, odo: t, paris: t, picard: t, pike: t, prerelease: t, reed: t, riker: t, sisko: t, spock: t, staging: t, sulu: t, tarpit: t, teams: t, tucker: t, wesley: t, worf: t }], crm: [0, { aa: a, ab: a, ac: a, ad: a, ae: a, af: a, ci: a, d: a, pa: a, pb: a, pc: a, pd: a, pe: a, pf: a, w: a, wa: a, wb: a, wc: a, wd: a, we: a, wf: a }], erp: Se, vercel: t, webhare: a, hrsn: t, "is-a": t }], dhl: e, diamonds: e, diet: e, digital: [1, { cloudapps: [2, { london: t }] }], direct: [1, { libp2p: t }], directory: e, discount: e, discover: e, dish: e, diy: K, dnp: e, docs: e, doctor: e, dog: e, domains: e, dot: e, download: e, drive: e, dtv: e, dubai: e, dupont: e, durban: e, dvag: e, dvr: e, earth: e, eat: e, eco: e, edeka: e, education: E, email: [1, { crisp: [0, { on: t }], tawk: qe, tawkto: qe }], emerck: e, energy: e, engineer: e, engineering: e, enterprises: e, epson: e, equipment: e, ericsson: e, erni: e, esq: e, estate: [1, { compute: a }], eurovision: e, eus: [1, { party: Nt }], events: [1, { koobin: t, co: t }], exchange: e, expert: e, exposed: e, express: e, extraspace: e, fage: e, fail: e, fairwinds: e, faith: e, family: e, fan: e, fans: e, farm: [1, { storj: t }], farmers: e, fashion: e, fast: e, fedex: e, feedback: e, ferrari: e, ferrero: e, fidelity: e, fido: e, film: e, final: e, finance: e, financial: E, fire: e, firestone: e, firmdale: e, fish: e, fishing: e, fit: e, fitness: e, flickr: e, flights: e, flir: e, florist: e, flowers: e, fly: e, foo: e, food: e, football: e, ford: e, forex: e, forsale: e, forum: e, foundation: e, fox: e, free: e, fresenius: e, frl: e, frogans: e, frontier: e, ftr: e, fujitsu: e, fun: e, fund: e, furniture: e, futbol: e, fyi: e, gal: e, gallery: e, gallo: e, gallup: e, game: e, games: [1, { pley: t, sheezy: t }], gap: e, garden: e, gay: [1, { pages: t }], gbiz: e, gdn: [1, { cnpy: t }], gea: e, gent: e, genting: e, george: e, ggee: e, gift: e, gifts: e, gives: e, giving: e, glass: e, gle: e, global: [1, { appwrite: t }], globo: e, gmail: e, gmbh: e, gmo: e, gmx: e, godaddy: e, gold: e, goldpoint: e, golf: e, goodyear: e, goog: [1, { cloud: t, translate: t, usercontent: a }], google: e, gop: e, got: e, grainger: e, graphics: e, gratis: e, green: e, gripe: e, grocery: e, group: K, gucci: e, guge: e, guide: e, guitars: e, guru: e, hair: e, hamburg: e, hangout: e, haus: e, hbo: e, hdfc: e, hdfcbank: e, health: [1, { hra: t }], healthcare: e, help: e, helsinki: e, here: e, hermes: e, hiphop: e, hisamitsu: e, hitachi: e, hiv: e, hkt: e, hockey: e, holdings: e, holiday: e, homedepot: e, homegoods: e, homes: e, homesense: e, honda: e, horse: e, hospital: e, host: [1, { cloudaccess: t, freesite: t, easypanel: t, emergent: t, fastvps: t, myfast: t, gadget: t, tempurl: t, wpmudev: t, iserv: t, jele: t, mircloud: t, bolt: t, wp2: t, half: t }], hosting: [1, { opencraft: t }], hot: e, hotel: e, hotels: e, hotmail: e, house: e, how: e, hsbc: e, hughes: e, hyatt: e, hyundai: e, ibm: e, icbc: e, ice: e, icu: e, ieee: e, ifm: e, ikano: e, imamat: e, imdb: e, immo: e, immobilien: e, inc: e, industries: e, infiniti: e, ing: e, ink: e, institute: e, insurance: e, insure: e, international: e, intuit: e, investments: e, ipiranga: e, irish: e, ismaili: e, ist: e, istanbul: e, itau: e, itv: e, jaguar: e, java: e, jcb: e, jeep: e, jetzt: e, jewelry: e, jio: e, jll: e, jmp: e, jnj: e, joburg: e, jot: e, joy: e, jpmorgan: e, jprs: e, juegos: e, juniper: e, kaufen: e, kddi: e, kerryhotels: e, kerryproperties: e, kfh: e, kia: e, kids: e, kim: e, kindle: e, kitchen: e, kiwi: e, koeln: e, komatsu: e, kosher: e, kpmg: e, kpn: e, krd: [1, { co: t, edu: t }], kred: e, kuokgroup: e, kyoto: e, lacaixa: e, lamborghini: e, lamer: e, land: e, landrover: e, lanxess: e, lasalle: e, lat: e, latino: e, latrobe: e, law: e, lawyer: e, lds: e, lease: e, leclerc: e, lefrak: e, legal: e, lego: e, lexus: e, lgbt: e, lidl: e, life: e, lifeinsurance: e, lifestyle: e, lighting: e, like: e, lilly: e, limited: e, limo: e, lincoln: e, link: [1, { myfritz: t, cyon: t, joinmc: t, dweb: a, inbrowser: a, keenetic: t, nftstorage: he, mypep: t, storacha: he, w3s: he }], live: [1, { aem: t, hlx: t, ewp: a }], living: e, llc: e, llp: e, loan: e, loans: e, locker: e, locus: e, lol: [1, { omg: t }], london: e, lotte: e, lotto: e, love: e, lpl: e, lplfinancial: e, ltd: e, ltda: e, lundbeck: e, luxe: e, luxury: e, madrid: e, maif: e, maison: e, makeup: e, man: e, management: e, mango: e, map: e, market: e, marketing: e, markets: e, marriott: e, marshalls: e, mattel: e, mba: e, mckinsey: e, med: e, media: R, meet: e, melbourne: e, meme: e, memorial: e, men: e, menu: [1, { barsy: t, barsyonline: t }], merck: e, merckmsd: e, miami: e, microsoft: e, mini: e, mint: e, mit: e, mitsubishi: e, mlb: e, mls: e, mma: e, mobile: e, moda: e, moe: e, moi: e, mom: e, monash: e, money: e, monster: e, mormon: e, mortgage: e, moscow: e, moto: e, motorcycles: e, mov: e, movie: e, msd: e, mtn: e, mtr: e, music: e, nab: e, nagoya: e, navy: e, nba: e, nec: e, netbank: e, netflix: e, network: [1, { aem: t, alces: a, appwrite: t, co: t, arvo: t, azimuth: t, tlon: t }], neustar: e, new: e, news: [1, { noticeable: t }], next: e, nextdirect: e, nexus: e, nfl: e, ngo: e, nhk: e, nico: e, nike: e, nikon: e, ninja: e, nissan: e, nissay: e, nokia: e, norton: e, now: e, nowruz: e, nowtv: e, nra: e, nrw: e, ntt: e, nyc: e, obi: e, observer: e, office: e, okinawa: e, olayan: e, olayangroup: e, ollo: e, omega: e, one: [1, { kin: a, service: t, website: t }], ong: e, onl: e, online: [1, { eero: t, "eero-stage": t, websitebuilder: t, leapcell: t, barsy: t }], ooo: e, open: e, oracle: e, orange: [1, { tech: t }], organic: e, origins: e, osaka: e, otsuka: e, ott: e, ovh: [1, { nerdpol: t }], page: [1, { aem: t, hlx: t, codeberg: t, deuxfleurs: t, heyflow: t, prvcy: t, rocky: t, statichost: t, pdns: t, plesk: t }], panasonic: e, paris: e, pars: e, partners: e, parts: e, party: e, pay: e, pccw: e, pet: e, pfizer: e, pharmacy: e, phd: e, philips: e, phone: e, photo: e, photography: e, photos: R, physio: e, pics: e, pictet: e, pictures: [1, { 1337: t }], pid: e, pin: e, ping: e, pink: e, pioneer: e, pizza: [1, { ngrok: t }], place: E, play: e, playstation: e, plumbing: e, plus: [1, { playit: [2, { at: a, with: t }] }], pnc: e, pohl: e, poker: e, politie: e, porn: e, praxi: e, press: e, prime: e, prod: e, productions: e, prof: e, progressive: e, promo: e, properties: e, property: e, protection: e, pru: e, prudential: e, pub: [1, { id: a, kin: a, barsy: t }], pwc: e, qpon: e, quebec: e, quest: e, racing: e, radio: e, read: e, realestate: e, realtor: e, realty: e, recipes: e, red: e, redumbrella: e, rehab: e, reise: e, reisen: e, reit: e, reliance: e, ren: e, rent: e, rentals: e, repair: e, report: e, republican: e, rest: e, restaurant: e, review: e, reviews: [1, { aem: t }], rexroth: e, rich: e, richardli: e, ricoh: e, ril: e, rio: e, rip: [1, { clan: t }], rocks: [1, { myddns: t, stackit: t, "lima-city": t, webspace: t }], rodeo: e, rogers: e, room: e, rsvp: e, rugby: e, ruhr: e, run: [1, { appwrite: a, canva: t, development: t, ravendb: t, liara: [2, { iran: t }], lovable: t, needle: t, build: a, code: a, database: a, migration: a, onporter: t, repl: t, stackit: t, val: Se, vercel: t, wix: t }], rwe: e, ryukyu: e, saarland: e, safe: e, safety: e, sakura: e, sale: e, salon: e, samsclub: e, samsung: e, sandvik: e, sandvikcoromant: e, sanofi: e, sap: e, sarl: e, sas: e, save: e, saxo: e, sbi: e, sbs: e, scb: e, schaeffler: e, schmidt: e, scholarships: e, school: e, schule: e, schwarz: e, science: e, scot: [1, { co: t, me: t, org: t, gov: [2, { service: t }] }], search: e, seat: e, secure: e, security: e, seek: e, select: e, sener: e, services: [1, { loginline: t }], seven: e, sew: e, sex: e, sexy: e, sfr: e, shangrila: e, sharp: e, shell: e, shia: e, shiksha: e, shoes: e, shop: [1, { base: t, hoplix: t, barsy: t, barsyonline: t, shopware: t }], shopping: e, shouji: e, show: e, silk: e, sina: e, singles: e, site: [1, { square: t, canva: I, cloudera: a, convex: t, cyon: t, caffeine: t, fastvps: t, figma: t, "figma-gov": t, preview: t, heyflow: t, jele: t, jouwweb: t, loginline: t, barsy: t, co: t, notion: t, omniwe: t, opensocial: t, madethis: t, support: t, platformsh: a, tst: a, byen: t, sol: t, srht: t, novecore: t, cpanel: t, wpsquared: t, sourcecraft: t }], ski: e, skin: e, sky: e, skype: e, sling: e, smart: e, smile: e, sncf: e, soccer: e, social: e, softbank: e, software: e, sohu: e, solar: e, solutions: e, song: e, sony: e, soy: e, spa: e, space: [1, { myfast: t, heiyu: t, hf: [2, { static: t }], "app-ionos": t, project: t, uber: t, xs4all: t }], sport: e, spot: e, srl: e, stada: e, staples: e, star: e, statebank: e, statefarm: e, stc: e, stcgroup: e, stockholm: e, storage: e, store: [1, { barsy: t, sellfy: t, shopware: t, storebase: t }], stream: e, studio: e, study: e, style: e, sucks: e, supplies: e, supply: e, support: [1, { barsy: t }], surf: e, surgery: e, suzuki: e, swatch: e, swiss: e, sydney: e, systems: [1, { knightpoint: t, miren: t }], tab: e, taipei: e, talk: e, taobao: e, target: e, tatamotors: e, tatar: e, tattoo: e, tax: e, taxi: e, tci: e, tdk: e, team: [1, { discourse: t, jelastic: t }], tech: [1, { cleverapps: t }], technology: E, temasek: e, tennis: e, teva: e, thd: e, theater: e, theatre: e, tiaa: e, tickets: e, tienda: e, tips: e, tires: e, tirol: e, tjmaxx: e, tjx: e, tkmaxx: e, tmall: e, today: [1, { prequalifyme: t }], tokyo: e, tools: [1, { addr: Fe, myaddr: t }], top: [1, { ntdll: t, wadl: a }], toray: e, toshiba: e, total: e, tours: e, town: e, toyota: e, toys: e, trade: e, trading: e, training: e, travel: e, travelers: e, travelersinsurance: e, trust: e, trv: e, tube: e, tui: e, tunes: e, tushu: e, tvs: e, ubank: e, ubs: e, unicom: e, university: e, uno: e, uol: e, ups: e, vacations: e, vana: e, vanguard: e, vegas: e, ventures: e, verisign: e, versicherung: e, vet: e, viajes: e, video: e, vig: e, viking: e, villas: e, vin: e, vip: [1, { hidns: t }], virgin: e, visa: e, vision: e, viva: e, vivo: e, vlaanderen: e, vodka: e, volvo: e, vote: e, voting: e, voto: e, voyage: e, wales: e, walmart: e, walter: e, wang: e, wanggou: e, watch: e, watches: e, weather: e, weatherchannel: e, webcam: e, weber: e, website: R, wed: e, wedding: e, weibo: e, weir: e, whoswho: e, wien: e, wiki: R, williamhill: e, win: e, windows: e, wine: e, winners: e, wme: e, wolterskluwer: e, woodside: e, work: [1, { "imagine-proxy": t }], works: e, world: e, wow: e, wtc: e, wtf: e, xbox: e, xerox: e, xihuan: e, xin: e, "xn--11b4c3d": e, कॉम: e, "xn--1ck2e1b": e, セール: e, "xn--1qqw23a": e, 佛山: e, "xn--30rr7y": e, 慈善: e, "xn--3bst00m": e, 集团: e, "xn--3ds443g": e, 在线: e, "xn--3pxu8k": e, 点看: e, "xn--42c2d9a": e, คอม: e, "xn--45q11c": e, 八卦: e, "xn--4gbrim": e, موقع: e, "xn--55qw42g": e, 公益: e, "xn--55qx5d": e, 公司: e, "xn--5su34j936bgsg": e, 香格里拉: e, "xn--5tzm5g": e, 网站: e, "xn--6frz82g": e, 移动: e, "xn--6qq986b3xl": e, 我爱你: e, "xn--80adxhks": e, москва: e, "xn--80aqecdr1a": e, католик: e, "xn--80asehdb": e, онлайн: e, "xn--80aswg": e, сайт: e, "xn--8y0a063a": e, 联通: e, "xn--9dbq2a": e, קום: e, "xn--9et52u": e, 时尚: e, "xn--9krt00a": e, 微博: e, "xn--b4w605ferd": e, 淡马锡: e, "xn--bck1b9a5dre4c": e, ファッション: e, "xn--c1avg": e, орг: e, "xn--c2br7g": e, नेट: e, "xn--cck2b3b": e, ストア: e, "xn--cckwcxetd": e, アマゾン: e, "xn--cg4bki": e, 삼성: e, "xn--czr694b": e, 商标: e, "xn--czrs0t": e, 商店: e, "xn--czru2d": e, 商城: e, "xn--d1acj3b": e, дети: e, "xn--eckvdtc9d": e, ポイント: e, "xn--efvy88h": e, 新闻: e, "xn--fct429k": e, 家電: e, "xn--fhbei": e, كوم: e, "xn--fiq228c5hs": e, 中文网: e, "xn--fiq64b": e, 中信: e, "xn--fjq720a": e, 娱乐: e, "xn--flw351e": e, 谷歌: e, "xn--fzys8d69uvgm": e, 電訊盈科: e, "xn--g2xx48c": e, 购物: e, "xn--gckr3f0f": e, クラウド: e, "xn--gk3at1e": e, 通販: e, "xn--hxt814e": e, 网店: e, "xn--i1b6b1a6a2e": e, संगठन: e, "xn--imr513n": e, 餐厅: e, "xn--io0a7i": e, 网络: e, "xn--j1aef": e, ком: e, "xn--jlq480n2rg": e, 亚马逊: e, "xn--jvr189m": e, 食品: e, "xn--kcrx77d1x4a": e, 飞利浦: e, "xn--kput3i": e, 手机: e, "xn--mgba3a3ejt": e, ارامكو: e, "xn--mgba7c0bbn0a": e, العليان: e, "xn--mgbab2bd": e, بازار: e, "xn--mgbca7dzdo": e, ابوظبي: e, "xn--mgbi4ecexp": e, كاثوليك: e, "xn--mgbt3dhd": e, همراه: e, "xn--mk1bu44c": e, 닷컴: e, "xn--mxtq1m": e, 政府: e, "xn--ngbc5azd": e, شبكة: e, "xn--ngbe9e0a": e, بيتك: e, "xn--ngbrx": e, عرب: e, "xn--nqv7f": e, 机构: e, "xn--nqv7fs00ema": e, 组织机构: e, "xn--nyqy26a": e, 健康: e, "xn--otu796d": e, 招聘: e, "xn--p1acf": [1, { "xn--90amc": t, "xn--j1aef": t, "xn--j1ael8b": t, "xn--h1ahn": t, "xn--j1adp": t, "xn--c1avg": t, "xn--80aaa0cvac": t, "xn--h1aliz": t, "xn--90a1af": t, "xn--41a": t }], рус: [1, { биз: t, ком: t, крым: t, мир: t, мск: t, орг: t, самара: t, сочи: t, спб: t, я: t }], "xn--pssy2u": e, 大拿: e, "xn--q9jyb4c": e, みんな: e, "xn--qcka1pmc": e, グーグル: e, "xn--rhqv96g": e, 世界: e, "xn--rovu88b": e, 書籍: e, "xn--ses554g": e, 网址: e, "xn--t60b56a": e, 닷넷: e, "xn--tckwe": e, コム: e, "xn--tiq49xqyj": e, 天主教: e, "xn--unup4y": e, 游戏: e, "xn--vermgensberater-ctb": e, vermögensberater: e, "xn--vermgensberatung-pwb": e, vermögensberatung: e, "xn--vhquv": e, 企业: e, "xn--vuq861b": e, 信息: e, "xn--w4r85el8fhu5dnra": e, 嘉里大酒店: e, "xn--w4rs40l": e, 嘉里: e, "xn--xhq521b": e, 广东: e, "xn--zfr164b": e, 政务: e, xyz: [1, { caffeine: t, botdash: t, telebit: a }], yachts: e, yahoo: e, yamaxun: e, yandex: e, yodobashi: e, yoga: e, yokohama: e, you: e, youtube: e, yun: e, zappos: e, zara: e, zero: e, zip: e, zone: [1, { triton: a, stackit: t, lima: t }], zuerich: e }];
})();
function _a(e, t, n, r) {
  let a = null, i = t;
  for (; i !== void 0 && ((i[0] & r) !== 0 && (a = {
    index: n + 1,
    isIcann: (i[0] & 1) !== 0,
    isPrivate: (i[0] & 2) !== 0
  }), n !== -1); ) {
    const s = i[1];
    i = Object.prototype.hasOwnProperty.call(s, e[n]) ? s[e[n]] : s["*"], n -= 1;
  }
  return a;
}
function Mo(e, t, n) {
  var r;
  if (Ro(e, t, n))
    return;
  const a = e.split("."), i = (t.allowPrivateDomains ? 2 : 0) | (t.allowIcannDomains ? 1 : 0), s = _a(a, No, a.length - 1, i);
  if (s !== null) {
    n.isIcann = s.isIcann, n.isPrivate = s.isPrivate, n.publicSuffix = a.slice(s.index + 1).join(".");
    return;
  }
  const o = _a(a, Ao, a.length - 1, i);
  if (o !== null) {
    n.isIcann = o.isIcann, n.isPrivate = o.isPrivate, n.publicSuffix = a.slice(o.index).join(".");
    return;
  }
  n.isIcann = !1, n.isPrivate = !1, n.publicSuffix = (r = a[a.length - 1]) !== null && r !== void 0 ? r : null;
}
const Ia = Co();
function Oo(e, t = {}) {
  return To(Ia), So(e, 3, Mo, t, Ia).domain;
}
var Po = class Ar extends Error {
  constructor(t) {
    super(t), this.name = "TimeoutError", Object.setPrototypeOf(this, Ar.prototype);
  }
};
async function Do(e, t) {
  return new Promise((n, r) => {
    const a = setTimeout(() => {
      r(new Po("Promise timed out."));
    }, t);
    e.then((i) => {
      clearTimeout(a), n(i);
    }).catch((i) => {
      clearTimeout(a), r(i);
    });
  });
}
var jo = "565487b2-a67c-4aac-bb92-3fb64732c7b8", Mr = Ri(
  ({ onLoad: e, scriptLocation: t }, n) => {
    const [r, a] = He(!1);
    return se(() => {
      window.hcaptcha && window.hcaptcha.render && a(!0);
    }, []), se(() => {
      r && e();
    }, [r, e]), /* @__PURE__ */ y(
      Ds,
      {
        sitekey: jo,
        onLoad: () => {
          a(!0);
        },
        ref: n,
        reCaptchaCompat: !1,
        scriptLocation: t
      }
    );
  }
);
Mr.displayName = "HCaptcha";
var Ca = "kapa-recaptcha-script", Bo = ({ siteKey: e, config: t, onLoad: n }) => {
  const { loadScript: r = !0 } = t || {}, [a, i] = He(!1);
  return se(() => {
    var s;
    if (!r) return;
    const o = () => {
      i(!0);
    }, l = (d) => {
      console.error("Failed to load reCAPTCHA Enterprise script", d);
    }, u = (() => {
      const d = document.getElementById(
        Ca
      );
      if (d)
        return d;
      const p = document.createElement("script");
      return p.id = Ca, p.src = `https://www.google.com/recaptcha/enterprise.js?render=${e}`, p.async = !0, p.defer = !0, document.head.appendChild(p), p;
    })();
    if ((s = window.grecaptcha) != null && s.enterprise) {
      i(!0);
      return;
    }
    return u.addEventListener("load", o), u.addEventListener("error", l), () => {
      u && (u.removeEventListener("load", o), u.removeEventListener("error", l));
    };
  }, [e, r]), se(() => {
    if (a && window.grecaptcha)
      try {
        window.grecaptcha.enterprise.ready(() => {
          n();
        });
      } catch (s) {
        console.error("Error during reCAPTCHA ready initialization:", s);
      }
  }, [a, n]), null;
}, zo = Bo, Ta = "6Lck4YwlAAAAAEIE1hR--varWp0qu9F-8-emQn2v", Or = W.createContext(void 0), Lo = ({ children: e, provider: t, hasConsent: n }) => {
  const r = W.useRef(null), a = W.useRef(null), [i, s] = W.useState(!1), o = W.useCallback(
    async (c) => {
      var u;
      if (!i)
        return console.error("HCaptcha is not ready"), null;
      try {
        const { response: d } = await ((u = r.current) == null ? void 0 : u.execute({ async: !0 }));
        return {
          token: d,
          key: "X-HCAPTCHA-TOKEN"
        };
      } catch (d) {
        return console.error("Error obtaining HCaptcha token:", d), null;
      }
    },
    [i]
  ), l = W.useCallback(
    async (c) => {
      if (!i)
        return console.error("reCAPTCHA is not ready"), null;
      try {
        return {
          token: await Do(
            window.grecaptcha.enterprise.execute(Ta, {
              action: c
            }),
            4e3
          ),
          key: "X-RECAPTCHA-ENTERPRISE-TOKEN"
        };
      } catch (u) {
        return console.error("Error obtaining reCAPTCHA token:", u), null;
      }
    },
    [i]
  );
  return /* @__PURE__ */ j(
    Or.Provider,
    {
      value: {
        executeCaptcha: t === "hcaptcha" ? o : l
      },
      children: [
        e,
        /* @__PURE__ */ j("div", { ref: a, style: { display: "none" }, children: [
          t === "hcaptcha" && n ? /* @__PURE__ */ y(
            Mr,
            {
              onLoad: () => {
                s(!0);
              },
              ref: r,
              scriptLocation: a.current
            }
          ) : null,
          t === "recaptcha" ? /* @__PURE__ */ y(
            zo,
            {
              onLoad: () => {
                s(!0);
              },
              siteKey: Ta,
              config: {
                loadScript: n
              }
            }
          ) : null
        ] })
      ]
    }
  );
}, Fo = () => {
  const e = W.useContext(Or);
  if (e === void 0)
    throw new Error("useConfig must be used within a CaptchaProvider");
  return e;
}, qo = class {
  constructor(e, t) {
    this.abortController = new AbortController(), this.proxyURL = "https://proxy.kapa.ai", this.processStreamFn = () => {
      throw new Error("processStream not implemented");
    }, t && (this.proxyURL = t), this.processStreamFn = e;
  }
  /**
   * Cancels any ongoing API request.
   * Creates a new AbortController for future requests.
   */
  abortCurrent() {
    this.abortController.abort(), this.abortController = new AbortController();
  }
  /**
   * @inheritdoc
   */
  async submitQuery({
    query: e,
    threadId: t,
    captcha: n,
    integrationId: r,
    sourceGroupIDsInclude: a,
    userIdentifiers: i,
    userMetadata: s,
    originUrl: o,
    mode: l = "default"
  }, c) {
    const {
      onFirstToken: u,
      onPartialAnswer: d,
      onMetadata: p,
      onRelevantSources: m,
      onError: h,
      onIdentifiers: g
    } = c;
    let v = `${this.proxyURL}/proxy/query/v2/chat/stream/`;
    t && (v = `${this.proxyURL}/proxy/query/v2/thread/${t}/chat/stream/`);
    try {
      const k = await fetch(v, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          [n.key]: n.token,
          "X-WEBSITE-ID": r
        },
        body: JSON.stringify({
          query: e,
          mode: l,
          ...a && a.length > 0 ? { source_group_ids_include: a } : {},
          user: {
            ...i,
            metadata: s
          },
          metadata: { origin_url: o },
          ...t ? { thread_id: t } : {}
        }),
        signal: this.abortController.signal
      });
      k.status === 200 && k.body ? await this.processStreamFn(k, {
        onFirstToken: u ?? (() => {
        }),
        onPartialAnswer: d ?? (() => {
        }),
        onRelevantSources: m ?? (() => {
        }),
        onError: h ?? (() => {
        }),
        onMetadata: p ?? (() => {
        }),
        onIdentifiers: (f, w) => {
          g?.(f, w);
        }
      }) : k.status === 403 && (await k.text()).toLowerCase().includes("captcha") ? h?.(
        "We noticed unusual activity. Please try asking your question again."
      ) : k.status === 429 ? (await k.json()).type === "deep_thinking_request_limit" ? h?.(
        "Too many deep thinking requests running at the moment. Please try again in a few seconds."
      ) : h?.(
        "There have been too many requests, please try again in a minute."
      ) : h?.(
        "Something went wrong. If the issue persists reach out to support."
      );
    } catch (k) {
      if (k?.name === "AbortError") return;
      throw h?.("Network error while fetching answer."), k;
    }
  }
  /**
   * @inheritdoc
   */
  async addFeedback({
    questionAnswerId: e,
    reaction: t,
    captcha: n,
    integrationId: r,
    comment: a
  }) {
    const i = `${this.proxyURL}/proxy/query/v2/feedback/upsert/`, s = {
      user_identifier: "user-identifier",
      // TODO: surface real id once backend supports it
      reaction: t,
      question_answer: e
    };
    if (a && (s.comment = JSON.stringify(a)), !(await fetch(i, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        [n.key]: n.token,
        "X-WEBSITE-ID": r
      },
      body: JSON.stringify(s)
    })).ok)
      throw new Error("Failed to send feedback");
  }
}, Pr = Ni(null), $o = ({
  children: e
}) => {
  const t = Q(null), n = {
    mode: "default",
    deepThinkingTimer: {
      seconds: 0,
      startedAt: null
    },
    enabled: !1
  }, r = (d, p) => {
    switch (p.type) {
      case "SET_MODE":
        return {
          ...d,
          mode: p.mode
        };
      case "START_DEEP_THINKING_TIMER":
        return {
          ...d,
          deepThinkingTimer: {
            startedAt: /* @__PURE__ */ new Date(),
            seconds: 0
          }
        };
      case "INCREMENT_DEEP_THINKING_TIMER_SECONDS": {
        const m = d.deepThinkingTimer.startedAt ? Math.floor(
          ((/* @__PURE__ */ new Date()).getTime() - d.deepThinkingTimer.startedAt.getTime()) / 1e3
          // convert milliseconds to seconds
        ) : 0;
        return {
          ...d,
          deepThinkingTimer: {
            ...d.deepThinkingTimer,
            seconds: m
          }
        };
      }
      case "RESET_DEEP_THINKING_TIMER":
        return {
          ...d,
          deepThinkingTimer: {
            seconds: 0,
            startedAt: null
          }
        };
      default:
        return d;
    }
  }, [a, i] = Ti(r, n), s = M(() => {
    i({
      type: "SET_MODE",
      mode: a.mode === "deep_thinking" ? "default" : "deep_thinking"
    });
  }, [i, a.mode]), o = M(() => {
    i({ type: "SET_MODE", mode: "deep_thinking" });
  }, [i]), l = M(() => {
    i({
      type: "START_DEEP_THINKING_TIMER"
    }), t.current = setInterval(() => {
      i({ type: "INCREMENT_DEEP_THINKING_TIMER_SECONDS" });
    }, 1e3);
  }, [i]), c = M(() => {
    t.current && clearInterval(t.current), i({ type: "RESET_DEEP_THINKING_TIMER" });
  }, [i]), u = M(() => {
    i({ type: "SET_MODE", mode: "default" });
  }, [i]);
  return /* @__PURE__ */ y(
    Pr.Provider,
    {
      value: {
        active: a.mode === "deep_thinking",
        seconds: a.deepThinkingTimer.seconds,
        toggle: s,
        activate: o,
        deactivate: u,
        startTimer: l,
        stopTimer: c
      },
      children: e
    }
  );
}, Uo = async (e, {
  onIdentifiers: t,
  onFirstToken: n,
  onRelevantSources: r,
  onPartialAnswer: a,
  onMetadata: i,
  onError: s
}) => {
  const o = e.body.getReader(), l = new TextDecoder("utf-8"), u = new TextEncoder().encode("␞");
  let d = new Uint8Array(), p = !0;
  const m = (g) => {
    for (let v = 0; v < g.length - u.length + 1; v++) {
      let k = !0;
      for (let f = 0; f < u.length; f++)
        if (g[v + f] !== u[f]) {
          k = !1;
          break;
        }
      if (k)
        return v;
    }
    return -1;
  };
  let h;
  for (; h = await o.read(), !h.done; ) {
    p && h.value.length > 0 && (n(), p = !1), d = new Uint8Array([...d, ...h.value]);
    let g;
    for (; (g = m(d)) !== -1; ) {
      const v = d.slice(0, g), k = l.decode(v);
      d = d.slice(g + u.length);
      const f = JSON.parse(k);
      switch (f.chunk.type) {
        case "relevant_sources": {
          r(f.chunk.content.relevant_sources);
          break;
        }
        case "partial_answer": {
          a(f.chunk.content.text);
          break;
        }
        case "metadata": {
          i({ is_uncertain: f.chunk.content.is_uncertain });
          break;
        }
        case "identifiers": {
          t(
            f.chunk.content.thread_id,
            f.chunk.content.question_answer_id
          );
          break;
        }
        case "error":
          s(f.chunk.content.reason);
          return;
      }
    }
  }
}, Dr = "@fpjs@client@";
function kt(e, t) {
  return `${t}__${e}`;
}
function jr(e, t) {
  return e.replace(`${t}__`, "");
}
var Go = class {
  constructor(e = Dr) {
    this.prefix = e;
  }
  set(e, t) {
    window.localStorage.setItem(kt(e, this.prefix), JSON.stringify(t));
  }
  get(e) {
    const t = window.localStorage.getItem(kt(e, this.prefix));
    if (t)
      try {
        return JSON.parse(t);
      } catch {
        return;
      }
  }
  remove(e) {
    window.localStorage.removeItem(kt(e, this.prefix));
  }
  allKeys() {
    return Object.keys(window.localStorage).filter((e) => e.startsWith(this.prefix)).map((e) => jr(e, this.prefix));
  }
}, Ho = class {
  constructor(e = Dr) {
    this.prefix = e;
  }
  /**
   * It takes a key and an entry, and sets the entry in the session storage with the key
   * @param {string} key - The key to store the entry under.
   * @param {Cacheable} entry - The value to be stored in the cache.
   */
  set(e, t) {
    window.sessionStorage.setItem(kt(e, this.prefix), JSON.stringify(t));
  }
  /**
   * It gets the value of the key from the session storage, parses it as JSON, and returns it
   * @param {string} key - The key to store the data under.
   * @returns The value of the key in the sessionStorage.
   */
  get(e) {
    const t = window.sessionStorage.getItem(kt(e, this.prefix));
    if (t)
      try {
        return JSON.parse(t);
      } catch {
        return;
      }
  }
  /**
   * It removes the item from session storage with the given key
   * @param {string} key - The key to store the value under.
   */
  remove(e) {
    window.sessionStorage.removeItem(kt(e, this.prefix));
  }
  /**
   * It returns an array of all the keys in the session storage that start with the prefix
   * @returns An array of all the keys in the sessionStorage that start with the prefix.
   */
  allKeys() {
    return Object.keys(window.sessionStorage).filter((e) => e.startsWith(this.prefix)).map((e) => jr(e, this.prefix));
  }
}, Vo = class {
  constructor() {
    this.enclosedCache = /* @__PURE__ */ (function() {
      const e = {};
      return {
        set(t, n) {
          e[t] = n;
        },
        get(t) {
          const n = e[t];
          if (n)
            return n;
        },
        remove(t) {
          delete e[t];
        },
        allKeys() {
          return Object.keys(e);
        }
      };
    })();
  }
}, Ko = class {
  set() {
  }
  get() {
  }
  remove() {
  }
  allKeys() {
    return [];
  }
}, ct;
(function(e) {
  e.Memory = "memory", e.LocalStorage = "localstorage", e.SessionStorage = "sessionstorage", e.NoCache = "nocache";
})(ct || (ct = {}));
ct.Memory + "", ct.LocalStorage + "", ct.SessionStorage + "", ct.NoCache + "";
var Sa = "kapa_web_id", Wo = (e) => {
  const { mode: t } = e, { data: n, getData: r } = Bs(), a = W.useCallback(() => {
    var o, l;
    if (t !== "cookie") return;
    let c = (o = ka.get(Sa)) != null ? o : crypto.randomUUID();
    const u = window.location.hostname, d = {
      domain: (l = Oo(u)) != null ? l : u,
      secure: !0,
      sameSite: "strict",
      expires: 399
    };
    return ka.set(Sa, c, d), c;
  }, [t]), i = W.useCallback(async () => {
    if (t === "fingerprint")
      try {
        return (await r()).visitorId;
      } catch (o) {
        console.error("Failed to fetch visitor analytics", o);
        return;
      }
  }, [t, n, r]);
  return {
    buildIdentifiers: W.useCallback(async () => {
      var o, l, c, u;
      const d = (o = window?.kapaSettings) == null ? void 0 : o.user, p = await i(), m = a(), h = {
        email: d?.email || void 0,
        unique_client_id: d?.uniqueClientId || void 0,
        fingerprint_id: p,
        kapa_web_id: m
      }, g = {
        company_name: ((l = d?.metadata) == null ? void 0 : l.companyName) || void 0,
        first_name: ((c = d?.metadata) == null ? void 0 : c.firstName) || void 0,
        last_name: ((u = d?.metadata) == null ? void 0 : u.lastName) || void 0
      };
      return { identifiers: h, metadata: g };
    }, [i, a])
  };
}, Ge = class Br extends Array {
  constructor(...t) {
    super(...t), Object.setPrototypeOf(this, Br.prototype);
  }
  getById(t) {
    return this.find((n) => n.id === t);
  }
  getLatest() {
    if (this.length !== 0)
      return this[this.length - 1];
  }
  /**
   * Retrieves the latest completed QA item from the collection.
   *
   * A completed QA item is identified by the presence of an `id` property.
   * If the collection is empty or no completed items are found, the method returns `undefined`.
   *
   * @returns {QA | undefined} The latest completed QA item, or `undefined` if none exist.
   */
  getLatestCompleted() {
    if (this.length === 0)
      return;
    const t = this.filter((r) => !!r.id);
    return t.length === 0 ? void 0 : t[t.length - 1];
  }
  // format the conversation history for the callbacks
  formatForCallbacks() {
    return this.map((t) => {
      var n;
      return {
        questionAnswerId: (n = t.id) != null ? n : "",
        question: t.question,
        answer: t.answer
      };
    });
  }
  setById(t, n) {
    const r = this.getById(t);
    r && (this[this.indexOf(r)] = { ...r, ...n });
  }
  setLatest(t) {
    if (this.length === 0)
      throw new Error("Cannot update latest item: array is empty");
    const n = this.length - 1, r = this[n];
    this[n] = { ...r, ...t };
  }
};
function zr(e) {
  return e.replace(/[#/]+$/, "");
}
function Xo(e) {
  if (!e) return ["", ""];
  const t = e.split(new RegExp("(?<=\\S)\\|(?=\\S)"));
  if (t.length === 1) {
    const a = t[0];
    if (a.startsWith("|"))
      return ["", a.substring(1)];
    if (e.endsWith("|"))
      return [e.substring(0, e.length - 1), ""];
  }
  const n = t[0] || "", r = t[1] || "";
  return [n, r];
}
function Qo(e) {
  return e.map((t) => {
    const [n, r] = Xo(t.title);
    return {
      ...t,
      title: n,
      subtitle: r
    };
  });
}
var Jo = (e) => {
  const t = e.replace(/```[\s\S]*?```/g, ""), n = /(http|https):\/\/([\w_-]+(?:(?:\.[\w_-]+)+))([\w.,@?^=%&:/+#-]*[\w@?^=%&/+#-])(?=[^\w@?^=%&/+#-]|$)/g, a = (t.match(n) || []).map((i) => zr(i));
  return new Set(a);
};
function Yo(e) {
  const t = /* @__PURE__ */ new Set();
  return e.reduce((n, r) => {
    const a = zr(r.source_url);
    return t.has(a) || (t.add(a), n.push({ ...r, source_url: a })), n;
  }, []);
}
var Zo = (e, t) => {
  const n = Yo(t), r = Jo(e);
  return n.filter(
    (a) => r?.has(a.source_url)
  );
};
function el(e, t) {
  var n, r;
  switch (t.type) {
    case "ADD_NEW_QA": {
      const a = {
        id: null,
        question: t.query,
        answer: "",
        sources: [],
        isGenerationAborted: !1,
        reaction: null,
        isFeedbackSubmissionEnabled: !1,
        status: "streaming"
      }, i = new Ge(...e.conversation, a);
      return {
        ...e,
        conversation: i,
        isPreparing: !0,
        error: null
      };
    }
    case "SET_IDENTIFIERS": {
      const a = new Ge(...e.conversation);
      return a.setLatest({
        id: t.questionAnswerId,
        isFeedbackSubmissionEnabled: !0,
        status: "pending_completion"
      }), {
        ...e,
        threadId: t.threadId,
        conversation: a
      };
    }
    case "SET_RELEVANT_SOURCES": {
      const a = e.conversation.getLatest();
      if (a) {
        const i = Zo(a.answer, t.sources), s = Qo(i), o = new Ge(...e.conversation);
        return o.setLatest({ sources: s }), {
          ...e,
          conversation: o
        };
      } else
        return console.error("Conversation is empty in order to set relevant sources"), e;
    }
    case "SET_PARTIAL_ANSWER": {
      const a = new Ge(...e.conversation), i = (r = (n = a.getLatest()) == null ? void 0 : n.answer) != null ? r : "";
      return a.setLatest({ answer: i + t.answer }), {
        ...e,
        conversation: a
      };
    }
    case "SET_METADATA": {
      const a = new Ge(...e.conversation);
      return a.setLatest({ metadata: t.metadata }), {
        ...e,
        conversation: a
      };
    }
    case "SET_ERROR":
      return {
        ...e,
        error: t.message,
        isPreparing: !1,
        isGenerating: !1
      };
    case "SET_IS_GENERATING":
      return {
        ...e,
        isPreparing: !1,
        isGenerating: t.isGenerating
      };
    case "STOP_GENERATION": {
      const a = new Ge(...e.conversation);
      return a.setLatest({ isGenerationAborted: !0 }), {
        ...e,
        conversation: a,
        isGenerating: !1,
        isPreparing: !1
      };
    }
    case "RESET_CONVERSATION":
      return {
        ...e,
        conversation: new Ge(),
        threadId: null,
        error: null
      };
    case "SET_FEEDBACK": {
      const a = new Ge(...e.conversation), i = a.getById(t.questionAnswerId);
      return i && (i.reaction = t.reaction), {
        ...e,
        conversation: a
      };
    }
    case "MARK_COMPLETED": {
      const a = new Ge(...e.conversation);
      return a.setById(t.questionAnswerId, { status: "completed" }), {
        ...e,
        conversation: a
      };
    }
    default:
      return e;
  }
}
var tl = "MHIUVE6Y4VV6H5lpgMyI", Lr = W.createContext(void 0), Ra = ({
  children: e,
  integrationId: t,
  sourceGroupIDsInclude: n,
  uncertainAnswerCallout: r,
  userTrackingMode: a,
  callbacks: i = {},
  apiService: s = new qo(Uo)
}) => {
  const l = W.useRef(s).current, c = {
    isGenerating: !1,
    isPreparing: !1,
    threadId: null,
    conversation: new Ge(),
    error: null
  }, u = Si(Pr), [d, p] = W.useReducer(el, c), { executeCaptcha: m } = Fo(), { buildIdentifiers: h } = Wo({
    mode: a
  }), g = W.useRef({
    onRelevantSources: (b) => p({ type: "SET_RELEVANT_SOURCES", sources: b }),
    onPartialAnswer: (b) => p({ type: "SET_PARTIAL_ANSWER", answer: b }),
    onMetadata: (b) => p({ type: "SET_METADATA", metadata: b }),
    onError: (b) => {
      p({ type: "SET_ERROR", message: b }), console.error("Error: ", b);
    },
    onFirstToken: () => p({ type: "SET_IS_GENERATING", isGenerating: !0 }),
    onIdentifiers: (b, I) => p({ type: "SET_IDENTIFIERS", threadId: b, questionAnswerId: I })
  }), v = W.useCallback(
    (b) => {
      var I, C, O;
      r && ((I = b.metadata) != null && I.is_uncertain) && p({
        type: "SET_PARTIAL_ANSWER",
        answer: r
      }), (O = (C = i.askAI) == null ? void 0 : C.onAnswerGenerationCompleted) == null || O.call(C, {
        threadId: d.threadId,
        conversation: d.conversation.formatForCallbacks(),
        question: b.question,
        answer: b.answer,
        questionAnswerId: b.id
      });
    },
    [
      r,
      i.askAI,
      d.threadId,
      d.conversation
    ]
  );
  W.useEffect(() => {
    d.conversation.filter(
      (I) => I.status === "pending_completion"
    ).forEach((I) => {
      v(I), p({ type: "MARK_COMPLETED", questionAnswerId: I.id });
    });
  }, [d.conversation, v]);
  const k = async (b) => {
    var I, C;
    p({ type: "ADD_NEW_QA", query: b }), (C = (I = i.askAI) == null ? void 0 : I.onQuerySubmit) == null || C.call(I, {
      threadId: d.threadId,
      conversation: d.conversation.formatForCallbacks(),
      question: b
    });
    const O = await m(
      "ask_ai"
      /* AskAi */
    );
    if (!O) {
      console.error(
        "Error in verifying browser for feedback submission. Captcha token could not be obtained."
      ), p({
        type: "SET_ERROR",
        message: "Error in verifying browser for feedback submission. Captcha token could not be obtained."
      });
      return;
    }
    if (d.isGenerating || d.isPreparing) {
      p({
        type: "SET_ERROR",
        message: "A new question was asked while the previous one was still being processed. Please wait until the previous question is answered."
      });
      return;
    }
    try {
      const { identifiers: q, metadata: A } = await h();
      u?.active && u.startTimer(), await l.submitQuery(
        {
          query: b,
          mode: u?.active ? "deep_thinking" : "default",
          threadId: d.threadId,
          captcha: O,
          integrationId: t,
          sourceGroupIDsInclude: n,
          userIdentifiers: q || {},
          userMetadata: A || {},
          originUrl: `${window.location.origin}${window.location.pathname}`
        },
        g.current
      );
    } catch (q) {
      console.error("Error making chat request:", q), p({
        type: "SET_ERROR",
        message: "An error occurred. Please try again."
      });
    } finally {
      p({ type: "SET_IS_GENERATING", isGenerating: !1 }), u?.active && (u.stopTimer(), u.deactivate());
    }
  }, f = () => {
    var b, I;
    p({ type: "RESET_CONVERSATION" }), (I = (b = i.askAI) == null ? void 0 : b.onConversationReset) == null || I.call(b, {
      threadId: d.threadId,
      conversation: d.conversation.formatForCallbacks()
    });
  }, w = () => {
    var b, I, C;
    l.abortCurrent(), p({ type: "STOP_GENERATION" }), (C = (b = i.askAI) == null ? void 0 : b.onAnswerGenerationStop) == null || C.call(b, {
      threadId: d.threadId,
      conversation: d.conversation.formatForCallbacks(),
      question: ((I = d.conversation.getLatest()) == null ? void 0 : I.question) || ""
    });
  }, E = go({
    mutationKey: ["feedback"],
    mutationFn: async ({
      questionAnswerId: b,
      reaction: I,
      comment: C
    }) => {
      var O, q, A, T;
      const _ = await m(
        "feedback_submit"
        /* FeedbackSubmit */
      );
      if (!_) {
        console.error(
          "Error in verifying browser for feedback submission. Captcha token could not be obtained."
        );
        return;
      }
      await l.addFeedback({
        questionAnswerId: b,
        reaction: I,
        captcha: _,
        integrationId: t,
        comment: C
      }), (T = (O = i.askAI) == null ? void 0 : O.onFeedbackSubmit) == null || T.call(O, {
        threadId: d.threadId,
        conversation: d.conversation.formatForCallbacks(),
        feedbackId: b,
        reaction: I,
        comment: C,
        questionAnswerId: b,
        question: ((q = d.conversation.getLatest()) == null ? void 0 : q.question) || "",
        answer: ((A = d.conversation.getLatest()) == null ? void 0 : A.answer) || ""
      });
    }
  }), x = (b, I, C) => {
    p({ type: "SET_FEEDBACK", questionAnswerId: b, reaction: I }), E.mutate(
      { questionAnswerId: b, reaction: I, comment: C },
      {
        onError: (O) => {
          console.error("Error sending feedback:", O), p({ type: "SET_FEEDBACK", questionAnswerId: b, reaction: null });
        }
      }
    );
  };
  return /* @__PURE__ */ y(
    Lr.Provider,
    {
      value: {
        integrationId: t,
        error: d.error,
        isPreparingAnswer: d.isPreparing,
        isGeneratingAnswer: d.isGenerating,
        conversation: d.conversation,
        callbacks: i,
        threadId: d.threadId,
        submitQuery: k,
        resetConversation: f,
        stopGeneration: w,
        addFeedback: x
      },
      children: e
    }
  );
}, nl = new mo({
  defaultOptions: {
    queries: { refetchOnWindowFocus: !1, retry: !1 },
    mutations: { retry: !1 }
  }
}), al = ({
  children: e,
  integrationId: t,
  sourceGroupIDsInclude: n,
  uncertainAnswerCallout: r,
  callbacks: a,
  userTrackingMode: i = "cookie",
  botProtectionMechanism: s,
  hasConsentForCaptcha: o = !0,
  apiService: l
}) => /* @__PURE__ */ y(fo, { client: nl, children: /* @__PURE__ */ y(
  Lo,
  {
    provider: s || "recaptcha",
    hasConsent: o,
    children: /* @__PURE__ */ y($o, { children: i === "fingerprint" ? /* @__PURE__ */ y(
      js,
      {
        loadOptions: {
          apiKey: tl,
          endpoint: "https://metrics.kapa.ai",
          scriptUrlPattern: "https://metrics.kapa.ai/web/v<version>/<apiKey>/loader_v<loaderVersion>.js"
        },
        cacheLocation: ct.LocalStorage,
        children: /* @__PURE__ */ y(
          Ra,
          {
            integrationId: t,
            sourceGroupIDsInclude: n,
            uncertainAnswerCallout: r,
            userTrackingMode: i,
            callbacks: a,
            apiService: l,
            children: e
          }
        )
      }
    ) : /* @__PURE__ */ y(
      Ra,
      {
        integrationId: t,
        sourceGroupIDsInclude: n,
        uncertainAnswerCallout: r,
        userTrackingMode: i,
        callbacks: a,
        apiService: l,
        children: e
      }
    ) })
  }
) });
function rl() {
  var e;
  const t = Mt.useContext(Lr);
  if (t === void 0)
    throw new Error("useChat must be used within a KapaProvider");
  return {
    isGeneratingAnswer: t.isGeneratingAnswer,
    isPreparingAnswer: t.isPreparingAnswer,
    error: (e = t.error) != null ? e : null,
    conversation: t.conversation,
    resetConversation: t.resetConversation,
    stopGeneration: t.stopGeneration,
    submitQuery: t.submitQuery,
    addFeedback: t.addFeedback,
    threadId: t.threadId
  };
}
const il = "_inputContainer_1e19u_9", sl = "_textArea_1e19u_18", ol = "_actionButton_1e19u_68", Vt = {
  inputContainer: il,
  textArea: sl,
  actionButton: ol
}, ll = ({
  onSend: e,
  onStop: t,
  isBusy: n = !1,
  isDisabled: r = !1,
  placeholder: a = "Type a message...",
  isOpen: i = !0
}) => {
  const [s, o] = He(""), l = Q(null), c = Q(!1), u = M(() => {
    const g = s.trim();
    g && !n && (e(g), o(""), l.current && (l.current.style.height = "auto"));
  }, [s, n, e]), d = M(() => {
    t?.();
  }, [t]), p = M(
    (g) => {
      g.key === "Enter" && !g.shiftKey && (g.preventDefault(), u());
    },
    [u]
  ), m = M((g) => {
    o(g.target.value);
  }, []);
  se(() => {
    const g = l.current;
    if (g) {
      g.style.height = "auto";
      const v = 40, k = 150;
      g.style.height = `${Math.max(v, Math.min(g.scrollHeight, k))}px`;
    }
  }, [s]), se(() => {
    i && l.current && l.current.focus();
  }, [i]), se(() => {
    if (n) {
      c.current = !0;
      return;
    }
    c.current && l.current && (l.current.focus(), c.current = !1);
  }, [n]);
  const h = s.trim().length > 0 && !n && !r;
  return /* @__PURE__ */ j("div", { className: Vt.inputContainer, children: [
    /* @__PURE__ */ y(
      "textarea",
      {
        ref: l,
        className: Vt.textArea,
        value: s,
        onChange: m,
        onKeyDown: p,
        placeholder: a,
        "aria-label": a,
        disabled: n || r,
        rows: 1
      }
    ),
    n ? /* @__PURE__ */ y(
      sa,
      {
        className: Vt.actionButton,
        kind: "ghost",
        size: "sm",
        hasIconOnly: !0,
        iconDescription: "Stop generation",
        "aria-label": "Stop generation",
        onClick: d,
        children: /* @__PURE__ */ y(ji, { slot: "icon" })
      }
    ) : /* @__PURE__ */ y(
      sa,
      {
        className: Vt.actionButton,
        kind: "ghost",
        size: "sm",
        hasIconOnly: !0,
        iconDescription: "Send message",
        "aria-label": "Send message",
        onClick: u,
        disabled: !h,
        children: /* @__PURE__ */ y(Bi, { slot: "icon" })
      }
    )
  ] });
}, Fr = (e, t = Date.now()) => {
  const n = new Date(e), r = new Date(t);
  if (n.getFullYear() === r.getFullYear() && n.getMonth() === r.getMonth() && n.getDate() === r.getDate())
    return n.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  const a = new Date(t);
  return a.setDate(a.getDate() - 1), n.getFullYear() === a.getFullYear() && n.getMonth() === a.getMonth() && n.getDate() === a.getDate() ? "Yesterday" : n.getFullYear() === r.getFullYear() ? n.toLocaleDateString([], { month: "short", day: "numeric" }) : n.toLocaleDateString([], { month: "short", day: "numeric", year: "numeric" });
}, cl = ({ content: e, streaming: t = !1, highlight: n = !0, ...r }) => e?.trim() ? /* @__PURE__ */ y(Ki, { highlight: n, streaming: t, ...r, children: e }) : null, ul = "_container_a43tv_9", dl = "_toggleButton_a43tv_16", pl = "_cardWrapper_a43tv_20", ml = "_cardBody_a43tv_25", hl = "_cardTitle_a43tv_32", fl = "_cardPreview_a43tv_42", gl = "_pagination_a43tv_53", yl = "_paginationLabel_a43tv_62", bl = "_launchIcon_a43tv_69", Ke = {
  container: ul,
  toggleButton: dl,
  cardWrapper: pl,
  cardBody: ml,
  cardTitle: hl,
  cardPreview: fl,
  pagination: gl,
  paginationLabel: yl,
  launchIcon: bl
}, Na = 150, vl = (e) => e.split("|")[0].trim(), kl = (e) => e.length <= Na ? e : `${e.slice(0, Na)}…`, wl = ({ sources: e }) => {
  const [t, n] = He(!1), [r, a] = He(0);
  if (e.length === 0)
    return null;
  const i = e[r], s = r > 0, o = r < e.length - 1;
  return /* @__PURE__ */ j("div", { className: Ke.container, children: [
    /* @__PURE__ */ y(
      rt,
      {
        kind: "ghost",
        size: "sm",
        onClick: () => n((l) => !l),
        renderIcon: t ? Li : hr,
        className: Ke.toggleButton,
        children: "Sources"
      }
    ),
    t && /* @__PURE__ */ j("div", { className: Ke.cardWrapper, children: [
      /* @__PURE__ */ y(Wi, { children: /* @__PURE__ */ j("div", { slot: "body", className: Ke.cardBody, children: [
        /* @__PURE__ */ y("span", { className: Ke.cardTitle, children: vl(i.title) }),
        i.content && /* @__PURE__ */ y("span", { className: Ke.cardPreview, children: kl(i.content) }),
        /* @__PURE__ */ y(ra, { href: i.sourceUrl, target: "_blank", rel: "noopener noreferrer", size: "sm", children: "View source" })
      ] }) }),
      e.length > 1 && /* @__PURE__ */ j("div", { className: Ke.pagination, children: [
        /* @__PURE__ */ y(
          at,
          {
            kind: "ghost",
            size: "sm",
            label: "Previous source",
            onClick: () => a((l) => l - 1),
            disabled: !s,
            children: /* @__PURE__ */ y(Fi, {})
          }
        ),
        /* @__PURE__ */ j("span", { className: Ke.paginationLabel, children: [
          r + 1,
          "/",
          e.length
        ] }),
        /* @__PURE__ */ y(
          at,
          {
            kind: "ghost",
            size: "sm",
            label: "Next source",
            onClick: () => a((l) => l + 1),
            disabled: !o,
            children: /* @__PURE__ */ y(fr, {})
          }
        ),
        /* @__PURE__ */ y(
          ra,
          {
            href: i.sourceUrl,
            target: "_blank",
            rel: "noopener noreferrer",
            "aria-label": "Open source in new tab",
            className: Ke.launchIcon,
            children: /* @__PURE__ */ y(qi, { size: 16 })
          }
        )
      ] })
    ] })
  ] });
}, xl = "_streamingIndicator_gh6ee_9", El = "_processingLabel_gh6ee_18", _l = "_collapsibleContainer_gh6ee_23", Il = "_collapsedContent_gh6ee_30", Kt = {
  streamingIndicator: xl,
  processingLabel: El,
  collapsibleContainer: _l,
  collapsedContent: Il
}, Cl = Vi, Tl = ({
  label: e = "Thinking...",
  completedLabel: t = "Thought process",
  content: n,
  isComplete: r
}) => {
  const [a, i] = He(!r), s = n && n.trim().length > 0;
  return r && s ? /* @__PURE__ */ j("div", { className: Kt.collapsibleContainer, children: [
    /* @__PURE__ */ y(
      rt,
      {
        kind: "ghost",
        size: "sm",
        renderIcon: a ? hr : fr,
        onClick: () => i(!a),
        "aria-expanded": a,
        children: t
      }
    ),
    a && /* @__PURE__ */ y("div", { className: Kt.collapsedContent, children: n })
  ] }) : r ? null : /* @__PURE__ */ j("div", { className: Kt.streamingIndicator, children: [
    /* @__PURE__ */ y(Cl, { loop: !0, carbonTheme: "g10" }),
    e && /* @__PURE__ */ y("span", { className: Kt.processingLabel, children: e })
  ] });
}, Sl = "_message_1jgvf_9", Rl = "_userMessage_1jgvf_16", Nl = "_assistantMessage_1jgvf_20", Al = "_userBubble_1jgvf_24", Ml = "_assistantBubble_1jgvf_36", Ol = "_timestamp_1jgvf_44", gt = {
  message: Sl,
  userMessage: Rl,
  assistantMessage: Nl,
  userBubble: Al,
  assistantBubble: Ml,
  timestamp: Ol
}, Pl = ({ message: e, isBusy: t }) => {
  const n = e.role === "user", r = e.status === "streaming" || e.status === "pending", a = e.status === "complete" || e.status === "error", i = e.status === "error", s = e.content && e.content.trim().length > 0, o = e.sources && e.sources.length > 0, l = n ? gt.userMessage : gt.assistantMessage;
  return /* @__PURE__ */ j("div", { id: e.id, className: `${gt.message} ${l}`, children: [
    e.timestamp && a && /* @__PURE__ */ y("time", { className: gt.timestamp, dateTime: new Date(e.timestamp).toISOString(), children: Fr(e.timestamp) }),
    s && n && /* @__PURE__ */ y("div", { className: gt.userBubble, children: e.content }),
    !n && r && !s && t !== !1 && /* @__PURE__ */ y(Tl, { label: e.statusLabel, isComplete: !1 }),
    s && !n && !i && /* @__PURE__ */ j("div", { className: gt.assistantBubble, children: [
      /* @__PURE__ */ y(cl, { content: e.content || "", streaming: r }),
      o && e.sources && /* @__PURE__ */ y(wl, { sources: e.sources })
    ] }),
    i && /* @__PURE__ */ y(
      pr,
      {
        kind: "error",
        title: "Error",
        subtitle: s ? e.content : "Something went wrong. Please try again.",
        hideCloseButton: !0
      }
    )
  ] });
}, Dl = 80, qr = (e) => [...e].reverse().find((t) => t.role === "user")?.id ?? e[0]?.id, jl = (e, t, n) => {
  const r = t.previousElementSibling;
  if (!r)
    return Math.max(0, e.clientHeight);
  const a = r.offsetTop + r.offsetHeight;
  if (a <= e.clientHeight)
    return 0;
  const i = a - n.offsetTop;
  return Math.max(0, e.clientHeight - i);
}, Bl = (e, t, n) => {
  const r = qr(e), a = r ? document.getElementById(r) : null;
  if (!a) {
    n.style.height = "0px";
    return;
  }
  n.style.height = `${jl(t, n, a)}px`;
}, zl = (e, t, n, { onScrollToTop: r, hasOlderMessages: a } = {}) => {
  const i = Q(0), s = Q(void 0), o = Q(void 0), l = Q(0), c = Q(!0), u = Q(!1), d = Q(r);
  d.current = r;
  const p = Q(a);
  p.current = a, se(() => {
    if (!u.current) return;
    const m = t.current;
    if (!m) return;
    u.current = !1;
    const h = () => {
      m.scrollTop = m.scrollHeight;
    };
    h();
    const g = new ResizeObserver(() => {
      h();
    });
    g.observe(m);
    const v = setTimeout(() => {
      g.disconnect();
    }, 600);
    return () => {
      g.disconnect(), clearTimeout(v);
    };
  }, [e, t]), se(() => {
    const m = t.current;
    if (!m)
      return;
    const h = () => {
      const g = m.scrollHeight - m.scrollTop - m.clientHeight;
      c.current = g < Dl, m.scrollTop === 0 && p.current && d.current?.();
    };
    return m.addEventListener("scroll", h, { passive: !0 }), () => m.removeEventListener("scroll", h);
  }, [t]), ur(() => {
    if (e.length === 0) {
      i.current = 0, s.current = void 0, o.current = void 0, l.current = 0;
      const E = n.current;
      E && (E.style.height = "0px");
      return;
    }
    const m = t.current, h = n.current;
    m && h && Bl(e, m, h);
    const g = e.length > i.current, v = s.current !== void 0 && e[0]?.id !== s.current, k = e[e.length - 1]?.id === o.current, f = g && v && k, w = g && !v;
    if (f && m)
      m.scrollTop += m.scrollHeight - l.current;
    else if (v)
      c.current = !0, u.current = !0, h && (h.style.height = "0px");
    else if (w && c.current)
      if (i.current === 0)
        u.current = !0;
      else {
        const E = qr(e), x = E ? document.getElementById(E) : null;
        x && x.scrollIntoView({ behavior: "smooth", block: "start" });
      }
    i.current = e.length, s.current = e[0]?.id, o.current = e[e.length - 1]?.id, l.current = m?.scrollHeight ?? 0;
  }, [e, t, n]);
}, Ll = "_container_1ou6v_9", Fl = "_status_1ou6v_17", ql = "_dot_1ou6v_23", $l = "_dotPast_1ou6v_30", Ul = "_dotCurrent_1ou6v_34", Gl = "_label_1ou6v_38", Hl = "_action_1ou6v_44", Vl = "_actionHidden_1ou6v_49", Ue = {
  container: Ll,
  status: Fl,
  dot: ql,
  dotPast: $l,
  dotCurrent: Ul,
  label: Gl,
  action: Hl,
  actionHidden: Vl
}, Kl = ({
  milestoneId: e,
  variant: t = "past",
  onRollback: n,
  onSeeVersion: r
}) => /* @__PURE__ */ j("div", { className: Ue.container, children: [
  /* @__PURE__ */ j("span", { className: Ue.status, children: [
    /* @__PURE__ */ y("span", { className: `${Ue.dot} ${t === "current" ? Ue.dotCurrent : Ue.dotPast}` }),
    /* @__PURE__ */ y("span", { className: Ue.label, children: "Version created" })
  ] }),
  /* @__PURE__ */ y(
    rt,
    {
      kind: "ghost",
      size: "xs",
      className: `${Ue.action} ${r ? "" : Ue.actionHidden}`,
      onClick: r ? () => r(e) : void 0,
      "aria-hidden": !r,
      tabIndex: r ? void 0 : -1,
      disabled: !r,
      children: "See version"
    }
  ),
  /* @__PURE__ */ y(
    rt,
    {
      kind: "ghost",
      size: "xs",
      className: `${Ue.action} ${n ? "" : Ue.actionHidden}`,
      onClick: n ? () => n(e) : void 0,
      "aria-hidden": !n,
      tabIndex: n ? void 0 : -1,
      children: "Restore version"
    }
  )
] }), Wl = "_container_11fip_9", Xl = "_list_11fip_16", Ql = "_emptyState_11fip_22", Jl = "_emptyTitle_11fip_33", Yl = "_emptyDescription_11fip_40", Zl = "_suggestions_11fip_44", ec = "_suggestionTag_11fip_53", tc = "_spacer_11fip_61", We = {
  container: Wl,
  list: Xl,
  emptyState: Ql,
  emptyTitle: Jl,
  emptyDescription: Yl,
  suggestions: Zl,
  suggestionTag: ec,
  spacer: tc
}, Aa = (e) => e.status === "complete" && !!e.milestoneId, nc = ({
  messages: e,
  emptyStateTitle: t = "Start a conversation",
  emptyStateDescription: n = "Ask me anything about your processes",
  suggestions: r,
  onSuggestionClick: a,
  onRollback: i,
  onSeeVersion: s,
  isBusy: o,
  hasOlderMessages: l,
  isLoadingOlderMessages: c,
  onLoadOlderMessages: u
}) => {
  const d = Q(null), p = Q(null);
  if (zl(e, d, p, { onScrollToTop: u, hasOlderMessages: l }), e.length === 0)
    return /* @__PURE__ */ y("div", { className: We.container, children: /* @__PURE__ */ j("div", { className: We.emptyState, children: [
      /* @__PURE__ */ y("p", { className: We.emptyTitle, children: t }),
      /* @__PURE__ */ y("p", { className: We.emptyDescription, children: n }),
      r && r.length > 0 && /* @__PURE__ */ y("div", { className: We.suggestions, children: r.map((h) => /* @__PURE__ */ y(
        mr,
        {
          type: "purple",
          className: We.suggestionTag,
          text: h.label,
          onClick: () => a?.(h)
        },
        h.label
      )) })
    ] }) }, "empty");
  const m = [...e].reverse().find(Aa)?.id;
  return /* @__PURE__ */ y("div", { className: We.container, children: /* @__PURE__ */ j("div", { ref: d, className: We.list, children: [
    c && /* @__PURE__ */ y(Ai, { description: "Loading older messages..." }),
    e.map((h) => /* @__PURE__ */ j(cr, { children: [
      /* @__PURE__ */ y(Pl, { message: h, isBusy: o }),
      (i || s) && Aa(h) && /* @__PURE__ */ y(
        Kl,
        {
          milestoneId: h.milestoneId,
          variant: h.id === m ? "current" : "past",
          onRollback: h.id === m ? void 0 : i,
          onSeeVersion: s
        }
      )
    ] }, h.id)),
    /* @__PURE__ */ y("div", { ref: p, className: We.spacer })
  ] }) }, "messages");
}, $r = {
  elem: "svg",
  attrs: {
    xmlns: "http://www.w3.org/2000/svg",
    viewBox: "0 0 32 32",
    fill: "currentColor",
    width: 16,
    height: 16
  },
  content: [
    {
      elem: "path",
      attrs: {
        d: "M26 3C27.0609 3 28.078 3.42173 28.8281 4.17188C29.5783 4.92202 30 5.93913 30 7V19C30 20.0609 29.5783 21.078 28.8281 21.8281C28.078 22.5783 27.0609 23 26 23H21.1602L17.7402 29L16 28L20 21H26C26.5304 21 27.039 20.7891 27.4141 20.4141C27.7891 20.039 28 19.5304 28 19V7C28 6.46957 27.7891 5.96101 27.4141 5.58594C27.039 5.21086 26.5304 5 26 5H6C5.46957 5 4.96101 5.21086 4.58594 5.58594C4.21086 5.96101 4 6.46957 4 7V19C4 19.5304 4.21086 20.039 4.58594 20.4141C4.96101 20.7891 5.46957 21 6 21H15V23H6C4.93913 23 3.92202 22.5783 3.17188 21.8281C2.42173 21.078 2 20.0609 2 19V7C2 5.93913 2.42173 4.92202 3.17188 4.17188C3.92202 3.42173 4.93913 3 6 3H26Z"
      }
    }
  ],
  name: "chat",
  size: 16
}, ac = {
  elem: "svg",
  attrs: {
    xmlns: "http://www.w3.org/2000/svg",
    viewBox: "0 0 32 32",
    fill: "currentColor",
    width: 16,
    height: 16
  },
  content: [
    {
      elem: "path",
      attrs: {
        d: "M16 2C23.7 2 30 8.3 30 16C30 23.7 23.7 30 16 30V28C22.6 28 28 22.6 28 16C28 9.4 22.6 4 16 4V2ZM8.2002 25.0996C9.3002 25.9996 10.5004 26.7002 11.9004 27.2002L11.2002 29.0996C9.60024 28.5996 8.19998 27.7002 7 26.7002L8.2002 25.0996ZM4.2002 18C4.40019 19.3999 4.89967 20.8 5.59961 22L3.90039 23C3.10046 21.6001 2.50022 20.0003 2.2002 18.4004L4.2002 18ZM17 15.5801L22 20.5898L20.5898 22L15 16.4102V7H17V15.5801ZM5.59961 10C4.89966 11.2 4.3998 12.5005 4.2998 13.9004L2.2998 13.5996C2.49985 11.9997 3.10045 10.3999 3.90039 9L5.59961 10ZM11.7998 4.7998C10.4998 5.2998 9.29978 5.99983 8.2998 6.7998L7 5.2998C8.19985 4.29995 9.59983 3.50035 11.0996 2.90039L11.7998 4.7998Z"
      }
    }
  ],
  name: "history",
  size: 16
}, Ur = ({ icon: e }) => /* @__PURE__ */ y("svg", { ...e.attrs, "aria-hidden": !0, focusable: !1, children: e.content.map((t, n) => /* @__PURE__ */ y("path", { ...t.attrs }, n)) }), rc = "data:image/svg+xml,%3csvg%20width='17'%20height='15'%20viewBox='0%200%2017%2015'%20fill='none'%20xmlns='http://www.w3.org/2000/svg'%3e%3cpath%20fill-rule='evenodd'%20clip-rule='evenodd'%20d='M9.7219%205.2691C8.6668%204.2126%207.7435%202.4757%207.0638%201.00772C7.0445%201.00316%207.0206%201%206.9941%201C6.9675%201%206.9436%201.00316%206.9243%201.00773C6.2447%202.4757%205.322%204.2122%204.2662%205.2687C3.2106%206.325%201.47501%207.2485%200.00802994%207.9287C0.00328994%207.9483%200%207.9728%200%208C0%208.0271%200.00324982%208.0515%200.00793982%208.071C1.47467%208.751%203.2101%209.6743%204.2659%2010.7311C5.322%2011.7875%206.2448%2013.5244%206.9245%2014.9923C6.9437%2014.9969%206.9675%2015%206.994%2015C7.0205%2015%207.0444%2014.9968%207.0636%2014.9923C7.7433%2013.5244%208.666%2011.7877%209.7215%2010.7315M9.7215%2010.7315C10.773%209.6791%2012.4531%208.7468%2013.8721%208.0577C13.8756%208.0415%2013.878%208.0217%2013.878%207.9998C13.878%207.9779%2013.8756%207.9582%2013.8721%207.942C12.4537%207.2529%2010.7734%206.3207%209.7219%205.2691'%20fill='%238a3ffc'/%3e%3cpath%20fill-rule='evenodd'%20clip-rule='evenodd'%20d='M15.4808%201.52468C15.104%201.14737%2014.7742%200.52704%2014.5315%200.00275993C14.5246%200.00112993%2014.5161%200%2014.5066%200C14.4971%200%2014.4885%200.00112993%2014.4817%200.00275993C14.2389%200.52705%2013.9094%201.14721%2013.5323%201.52455C13.1553%201.9018%2012.5355%202.2316%2012.0116%202.4745C12.0099%202.4815%2012.0087%202.4903%2012.0087%202.5C12.0087%202.5097%2012.0099%202.5184%2012.0115%202.5254C12.5354%202.7682%2013.1552%203.098%2013.5322%203.4754C13.9094%203.8527%2014.239%204.473%2014.4817%204.9972C14.4886%204.9989%2014.4971%205%2014.5066%205C14.516%205%2014.5246%204.9989%2014.5314%204.9972C14.7742%204.473%2015.1037%203.8528%2015.4806%203.4755M15.4806%203.4755C15.8562%203.0997%2016.4562%202.7667%2016.963%202.5206C16.9643%202.5148%2016.9651%202.5078%2016.9651%202.4999C16.9651%202.4921%2016.9643%202.4851%2016.963%202.4793C16.4565%202.2332%2015.8563%201.90026%2015.4808%201.52468'%20fill='%238a3ffc'/%3e%3c/svg%3e", ic = "_header_1ufpr_9", sc = "_iconWrapper_1ufpr_16", oc = "_icon_1ufpr_16", lc = "_titleWrapper_1ufpr_31", cc = "_titleRow_1ufpr_37", uc = "_title_1ufpr_31", dc = "_earlyAccessLink_1ufpr_50", pc = "_subtitle_1ufpr_62", mc = "_actions_1ufpr_68", hc = "_actionButton_1ufpr_74", De = {
  header: ic,
  iconWrapper: sc,
  icon: oc,
  titleWrapper: lc,
  titleRow: cc,
  title: uc,
  earlyAccessLink: dc,
  subtitle: pc,
  actions: mc,
  actionButton: hc
}, Ma = "Disabled until thinking is complete", fc = ({
  onReset: e,
  onShowHistory: t,
  conversationTitle: n,
  view: r = "chat",
  isBusy: a = !1
}) => {
  const i = r === "history";
  return /* @__PURE__ */ j(Xi, { actions: [], className: De.header, children: [
    /* @__PURE__ */ y("div", { className: De.iconWrapper, slot: "navigation", children: /* @__PURE__ */ y("img", { src: rc, alt: "", className: De.icon }) }),
    /* @__PURE__ */ j("div", { slot: "title", className: De.titleWrapper, children: [
      /* @__PURE__ */ j("div", { className: De.titleRow, children: [
        /* @__PURE__ */ y("span", { className: De.title, children: "Camunda Copilot" }),
        /* @__PURE__ */ y(
          "a",
          {
            href: "https://docs.camunda.io/docs/next/components/early-access/alpha/alpha-features/",
            target: "_blank",
            rel: "noopener noreferrer",
            className: De.earlyAccessLink,
            children: /* @__PURE__ */ y(mr, { type: "gray", size: "sm", text: "Early access" })
          }
        )
      ] }),
      n && /* @__PURE__ */ y("span", { className: De.subtitle, children: n })
    ] }),
    /* @__PURE__ */ j("div", { slot: "fixed-actions", className: De.actions, children: [
      e && /* @__PURE__ */ y(
        at,
        {
          kind: "ghost",
          size: "lg",
          label: a ? Ma : "New conversation",
          align: "bottom-right",
          onClick: e,
          disabled: a,
          className: De.actionButton,
          children: /* @__PURE__ */ y(zi, {})
        }
      ),
      t && /* @__PURE__ */ y(
        at,
        {
          kind: "ghost",
          size: "lg",
          label: a ? Ma : i ? "Back to chat" : "Conversation history",
          align: "bottom-right",
          onClick: t,
          disabled: a,
          className: De.actionButton,
          children: /* @__PURE__ */ y(Ur, { icon: i ? $r : ac })
        }
      )
    ] })
  ] });
}, gc = "_container_db17r_9", yc = "_tag_db17r_18", Oa = {
  container: gc,
  tag: yc
}, mn = (e, t) => e.id ?? `${e.label}-${t}`, Pa = (e) => e?.map((t) => `${t.id ?? ""}:${t.label}`).join("|") ?? "", bc = ({ items: e, onRemoveItem: t }) => {
  const [n, r] = He(/* @__PURE__ */ new Set()), a = Q(Pa(e));
  se(() => {
    const o = Pa(e);
    o !== a.current && (a.current = o, r(/* @__PURE__ */ new Set()));
  }, [e]);
  const i = dr(
    () => e?.filter((o, l) => !n.has(mn(o, l))) ?? [],
    [e, n]
  ), s = M(
    (o, l) => {
      r((c) => /* @__PURE__ */ new Set([...c, mn(o, l)])), t?.(o);
    },
    [t]
  );
  return i.length ? /* @__PURE__ */ y("div", { className: Oa.container, "aria-label": "Active context", children: i.map((o, l) => /* @__PURE__ */ y(
    Mi,
    {
      type: "purple",
      size: "sm",
      text: o.label,
      onClose: () => s(o, l),
      className: Oa.tag
    },
    mn(o, l)
  )) }) : null;
}, Da = (e) => {
  let t;
  const n = /* @__PURE__ */ new Set(), r = (c, u) => {
    const d = typeof c == "function" ? c(t) : c;
    if (!Object.is(d, t)) {
      const p = t;
      t = u ?? (typeof d != "object" || d === null) ? d : Object.assign({}, t, d), n.forEach((m) => m(t, p));
    }
  }, a = () => t, o = { setState: r, getState: a, getInitialState: () => l, subscribe: (c) => (n.add(c), () => n.delete(c)) }, l = t = e(r, a, o);
  return o;
}, vc = ((e) => e ? Da(e) : Da), kc = (e) => e;
function wc(e, t = kc) {
  const n = Mt.useSyncExternalStore(
    e.subscribe,
    Mt.useCallback(() => t(e.getState()), [e, t]),
    Mt.useCallback(() => t(e.getInitialState()), [e, t])
  );
  return Mt.useDebugValue(n), n;
}
const ja = (e) => {
  const t = vc(e), n = (r) => wc(t, r);
  return Object.assign(n, t), n;
}, on = ((e) => e ? ja(e) : ja), Ba = () => `msg-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`, xc = (e) => ({
  addUserMessage: (t) => {
    const n = Ba();
    return e((r) => ({
      messages: [
        ...r.messages,
        { id: n, role: "user", content: t, status: "complete", timestamp: Date.now() }
      ]
    })), n;
  },
  addAssistantMessage: (t) => {
    e((n) => ({
      messages: [
        ...n.messages,
        { id: t, role: "assistant", content: "", status: "pending", timestamp: Date.now() }
      ],
      isStreaming: !0,
      streamingMessageId: t
    }));
  },
  appendToMessage: (t, n) => {
    e((r) => ({
      messages: r.messages.map(
        (a) => a.id === t ? { ...a, content: a.content + n, status: "streaming" } : a
      )
    }));
  },
  setMessageContent: (t, n) => {
    e((r) => ({
      messages: r.messages.map((a) => a.id === t ? { ...a, content: n, status: "streaming" } : a)
    }));
  },
  updateMessageStatus: (t, n) => {
    e((r) => ({
      messages: r.messages.map((a) => a.id !== t || a.status === "error" && n === "complete" ? a : { ...a, status: n }),
      isStreaming: n === "streaming" || n === "pending",
      streamingMessageId: n === "complete" || n === "error" ? null : r.streamingMessageId
    }));
  },
  updateMessageStatusLabel: (t, n) => {
    e((r) => ({
      messages: r.messages.map((a) => a.id === t ? { ...a, statusLabel: n } : a)
    }));
  },
  setMessageSources: (t, n) => {
    e((r) => ({
      messages: r.messages.map((a) => a.id === t ? { ...a, sources: n } : a)
    }));
  },
  setStreaming: (t, n = null) => {
    e({ isStreaming: t, streamingMessageId: n });
  },
  clearMessages: () => {
    e({
      view: "chat",
      messages: [],
      isStreaming: !1,
      streamingMessageId: null,
      currentThinkingBlockId: null,
      conversationTitle: null
    });
  },
  setView: (t) => {
    e({ view: t });
  },
  setConversationTitle: (t) => {
    e({ conversationTitle: t });
  },
  loadConversation: (t, n) => {
    e({
      view: "chat",
      messages: t,
      conversationTitle: n,
      isStreaming: !1,
      streamingMessageId: null,
      currentThinkingBlockId: null
    });
  },
  prependMessages: (t) => {
    e((n) => ({ messages: [...t, ...n.messages] }));
  },
  setMessageError: (t, n) => {
    e((r) => ({
      messages: r.messages.map(
        (a) => a.id === t ? { ...a, content: n, status: "error" } : a
      ),
      isStreaming: !1,
      streamingMessageId: null
    }));
  },
  rollbackToMessage: (t) => {
    e((n) => {
      const r = n.messages.findIndex((a) => a.id === t);
      return r === -1 ? n : {
        messages: n.messages.slice(0, r + 1),
        isStreaming: !1,
        streamingMessageId: null,
        currentThinkingBlockId: null
      };
    });
  },
  addRollbackMessage: (t, n) => {
    const r = Ba();
    e((a) => ({
      messages: [
        ...a.messages,
        { id: r, role: "assistant", content: t, status: "complete", timestamp: Date.now(), milestoneId: n }
      ]
    }));
  },
  setLastAssistantMessageMilestone: (t) => {
    e((n) => {
      const r = [...n.messages].reverse().findIndex((i) => i.role === "assistant" && i.status === "complete");
      if (r === -1) return n;
      const a = n.messages.length - 1 - r;
      return {
        messages: n.messages.map((i, s) => s === a ? { ...i, milestoneId: t } : i)
      };
    });
  },
  setLastUserMessageMilestone: (t) => {
    e((n) => {
      const r = [...n.messages].reverse().findIndex((i) => i.role === "user");
      if (r === -1) return n;
      const a = n.messages.length - 1 - r;
      return {
        messages: n.messages.map((i, s) => s === a ? { ...i, milestoneId: t } : i)
      };
    });
  }
}), ve = /* @__PURE__ */ new Map(), Ee = {
  registerTool: (e) => {
    const { handler: t, ...n } = e;
    if (n.type === "mixed") {
      ve.set(n.name, { definition: n, mixedHandler: t });
      return;
    }
    ve.set(n.name, { definition: n, handler: t });
  },
  has: (e) => ve.has(e),
  hasHandler: (e) => {
    const t = ve.get(e);
    return t?.handler !== void 0 || t?.mixedHandler !== void 0;
  },
  isMixed: (e) => ve.get(e)?.definition.type === "mixed",
  getDisplayName: (e) => ve.get(e)?.definition.displayName,
  getDescription: (e, t) => {
    const n = ve.get(e), r = n?.definition.displayName ?? e.replace(/_/g, " ");
    if (!n?.definition.descriptionFormatter)
      return r;
    try {
      return n.definition.descriptionFormatter(t) ?? r;
    } catch {
      return r;
    }
  },
  hasCustomDescription: (e) => {
    const t = ve.get(e);
    return !!(t?.definition.descriptionFormatter || t?.definition.displayName);
  },
  getContentType: (e) => ve.get(e)?.definition.contentType ?? "TEXT",
  getMixedHandler: (e) => ve.get(e)?.mixedHandler,
  getHandler: (e) => ve.get(e)?.handler,
  executeHandler: async (e, t, n) => {
    const r = ve.get(e)?.handler;
    if (!r)
      throw new Error(`No handler registered for tool: ${e}`);
    return r(t, n);
  },
  getToolsForBackend: () => Array.from(ve.values()).filter((e) => e.definition.type === "frontend" && e.handler !== void 0).map(({ definition: e }) => ({
    name: e.name,
    description: e.description,
    parameters: e.parameters,
    category: e.category
  })),
  clear: () => {
    ve.clear();
  }
}, Ec = (e) => ({
  addThinkingBlock: (t, n, r) => {
    const a = { id: n, content: "", label: r, isComplete: !1 };
    e((i) => ({
      messages: i.messages.map(
        (s) => s.id === t ? { ...s, thinkingBlocks: [...s.thinkingBlocks ?? [], a] } : s
      ),
      currentThinkingBlockId: n
    }));
  },
  appendToThinkingBlock: (t, n, r) => {
    e((a) => ({
      messages: a.messages.map(
        (i) => i.id === t ? {
          ...i,
          thinkingBlocks: (i.thinkingBlocks ?? []).map(
            (s) => s.id === n ? { ...s, content: s.content + r } : s
          )
        } : i
      )
    }));
  },
  completeThinkingBlock: (t, n) => {
    e((r) => ({
      messages: r.messages.map(
        (a) => a.id === t ? {
          ...a,
          thinkingBlocks: (a.thinkingBlocks ?? []).map(
            (i) => i.id === n ? { ...i, isComplete: !0 } : i
          )
        } : a
      ),
      currentThinkingBlockId: null
    }));
  },
  completeAllThinkingBlocks: (t) => {
    e((n) => ({
      messages: n.messages.map(
        (r) => r.id === t ? { ...r, thinkingBlocks: (r.thinkingBlocks ?? []).map((a) => ({ ...a, isComplete: !0 })) } : r
      ),
      currentThinkingBlockId: null
    }));
  },
  addToolBlock: (t, n, r, a, i) => {
    const s = Ee.getDescription(r, i ?? {}), o = Ee.hasCustomDescription(r) ? `${s}...` : `Running ${s}...`, l = {
      id: n,
      content: "",
      label: r,
      isComplete: !1,
      kind: "tool",
      toolName: r,
      toolStatus: "processing",
      stepNumber: a,
      toolArguments: i,
      description: s
    };
    e((c) => ({
      messages: c.messages.map(
        (u) => u.id === t ? { ...u, thinkingBlocks: [...u.thinkingBlocks ?? [], l], statusLabel: o } : u
      )
    }));
  },
  updateToolBlockStatus: (t, n, r) => {
    e((a) => ({
      messages: a.messages.map(
        (i) => i.id === t ? {
          ...i,
          thinkingBlocks: (i.thinkingBlocks ?? []).map(
            (s) => s.id === n ? { ...s, toolStatus: r, isComplete: r === "success" || r === "error" } : s
          )
        } : i
      )
    }));
  },
  updateToolBlockResult: (t, n, r) => {
    e((a) => ({
      messages: a.messages.map(
        (i) => i.id === t ? {
          ...i,
          thinkingBlocks: (i.thinkingBlocks ?? []).map(
            (s) => s.id === n ? { ...s, toolResult: r } : s
          )
        } : i
      )
    }));
  },
  addErrorBlock: (t, n, r) => {
    const a = {
      id: n,
      content: r,
      label: "Error",
      isComplete: !0,
      kind: "error"
    };
    e((i) => ({
      messages: i.messages.map(
        (s) => s.id === t ? { ...s, thinkingBlocks: [...s.thinkingBlocks ?? [], a] } : s
      )
    }));
  }
}), _c = {
  view: "chat",
  messages: [],
  isStreaming: !1,
  streamingMessageId: null,
  currentThinkingBlockId: null,
  conversationTitle: null
}, F = on((e) => ({
  ..._c,
  ...xc(e),
  ...Ec(e)
})), H = {
  THINKING: "THINKING",
  EXECUTION_PLAN: "EXECUTION_PLAN",
  TOOL_PLANNING: "TOOL_PLANNING",
  TOOL_INVOKE: "TOOL_INVOKE",
  EXTERNAL_TOOL_CALL: "EXTERNAL_TOOL_CALL",
  TOOL_RESULT: "TOOL_RESULT",
  EXECUTION_COMPLETE: "EXECUTION_COMPLETE",
  ERROR: "ERROR",
  CONVERSATION_TITLE: "CONVERSATION_TITLE"
}, oe = {
  IN_PROGRESS: "IN_PROGRESS",
  COMPLETED: "COMPLETED",
  ERROR: "ERROR"
}, ln = {
  TOOL_INVOKE: "TOOL_INVOKE",
  MIXED_TOOL_RESULT: "MIXED_TOOL_RESULT",
  CONVERSATION_TITLE: "CONVERSATION_TITLE"
}, Wf = {
  IDLE: "IDLE",
  BUSY: "BUSY",
  AWAITING_TOOL: "AWAITING_TOOL",
  ERROR: "ERROR"
}, Gr = (e) => {
  if (!e) return {};
  try {
    return JSON.parse(e);
  } catch {
    return {};
  }
}, Ic = ({ event: e }) => e.status === oe.ERROR ? {
  stateUpdate: {
    currentEventType: null,
    currentToolName: null,
    pendingToolInvoke: null
  },
  result: null,
  clearStatusLabel: !0
} : null, Cc = ({ event: e }) => e.type === H.EXECUTION_COMPLETE ? {
  stateUpdate: {
    isBusy: !1,
    currentEventType: null,
    currentToolName: null,
    pendingToolInvoke: null
  },
  result: null,
  messageStatus: "complete",
  clearStatusLabel: !0
} : null, Tc = ({ event: e, state: t }) => {
  if (e.status !== oe.IN_PROGRESS) return null;
  const n = {
    currentEventType: e.type,
    currentToolName: e.toolName || null
  }, r = e.toolName;
  if (!r)
    return { stateUpdate: n, result: null };
  if (Ee.isMixed(r))
    return { stateUpdate: n, result: null };
  if (!(e.type === H.EXTERNAL_TOOL_CALL && Ee.has(r) && !t.pendingToolInvoke))
    return { stateUpdate: n, result: null };
  const i = Gr(e.toolArguments);
  return {
    stateUpdate: {
      ...n,
      pendingToolInvoke: { toolName: r, toolCallId: e.toolCallId, toolArguments: i }
    },
    result: {
      type: ln.TOOL_INVOKE,
      toolName: r,
      toolCallId: e.toolCallId,
      toolArguments: i
    }
  };
}, Sc = ({ event: e, state: t }) => e.status === oe.COMPLETED && e.type === H.TOOL_RESULT && e.toolName && t.pendingToolInvoke?.toolName === e.toolName ? { stateUpdate: { pendingToolInvoke: null }, result: null } : null, Rc = ({ event: e }) => {
  const t = e.toolName;
  return !(e.status === oe.COMPLETED && e.type === H.TOOL_RESULT && t && Ee.isMixed(t) && e.toolResult) || !t || !e.toolResult ? null : {
    stateUpdate: null,
    result: {
      type: ln.MIXED_TOOL_RESULT,
      toolName: t,
      toolResult: e.toolResult
    }
  };
}, Nc = [
  Ic,
  Cc,
  Tc,
  Rc,
  Sc
], Ac = (e, t) => {
  const n = { event: e, state: t, messageId: t.currentMessageId };
  return Nc.reduce((r, a) => a(n) ?? r, {
    stateUpdate: null,
    result: null
  });
}, za = {
  conversationId: null,
  currentMessageId: null,
  isBusy: !1,
  currentEventType: null,
  currentToolName: null,
  pendingToolInvoke: null,
  error: null
}, Mc = (e) => e.isBusy || e.pendingToolInvoke !== null, Xf = (e) => e.error ? "ERROR" : e.pendingToolInvoke ? "AWAITING_TOOL" : e.isBusy ? "BUSY" : "IDLE", G = on((e, t) => ({
  ...za,
  callbacks: {},
  setCallbacks: (n) => {
    e({ callbacks: n });
  },
  startConversation: (n, r) => {
    t().isBusy || e({
      conversationId: n,
      currentMessageId: r,
      isBusy: !0,
      currentEventType: null,
      currentToolName: null,
      pendingToolInvoke: null,
      error: null
    });
  },
  processEvent: (n) => {
    const r = t(), a = r.currentMessageId, { stateUpdate: i, result: s, messageStatus: o, clearStatusLabel: l } = Ac(n, r);
    return i && e(i), a && o && r.callbacks.onMessageStatusUpdate && r.callbacks.onMessageStatusUpdate(a, o), a && l && r.callbacks.onStatusLabelClear && r.callbacks.onStatusLabelClear(a, ""), s;
  },
  clearPendingTool: () => {
    e({ pendingToolInvoke: null });
  },
  setError: (n) => {
    const r = t(), a = r.currentMessageId;
    e({
      isBusy: !1,
      currentEventType: null,
      currentToolName: null,
      pendingToolInvoke: null,
      error: n
    }), a && r.callbacks.onMessageError ? r.callbacks.onMessageError(a, n) : a && r.callbacks.onMessageStatusUpdate && r.callbacks.onMessageStatusUpdate(a, "error"), a && r.callbacks.onStatusLabelClear && r.callbacks.onStatusLabelClear(a, "");
  },
  reset: () => {
    const n = t(), r = n.currentMessageId;
    r && n.callbacks.onMessageStatusUpdate && n.callbacks.onMessageStatusUpdate(r, "complete"), r && n.callbacks.onStatusLabelClear && n.callbacks.onStatusLabelClear(r, ""), e({ ...za, callbacks: n.callbacks });
  },
  setConversationId: (n) => {
    e({ conversationId: n });
  }
})), xt = on((e) => ({
  isOpen: !1,
  open: () => e({ isOpen: !0 }),
  close: () => e({ isOpen: !1 }),
  toggle: () => e((t) => ({ isOpen: !t.isOpen }))
})), Oc = { messages: [], pendingToolBlocks: [] }, Pc = /* @__PURE__ */ new Set(["USER", "ASSISTANT", "TOOL_CALL", "TOOL_OUTPUT"]), La = (e) => {
  const { messages: t, pendingToolBlocks: n } = e.filter((r) => Pc.has(r.type)).reduce(Dc, Oc);
  return n.length > 0 ? jc(t, n) : t;
}, Dc = (e, t) => {
  if (t.type === "USER")
    return { ...e, messages: [...e.messages, Bc(t)] };
  if (t.type === "ASSISTANT") {
    const n = e.pendingToolBlocks.length > 0 ? { ...Fa(t), thinkingBlocks: e.pendingToolBlocks } : Fa(t);
    return { messages: [...e.messages, n], pendingToolBlocks: [] };
  }
  return { ...e, pendingToolBlocks: [...e.pendingToolBlocks, zc(t)] };
}, jc = (e, t) => {
  const n = [...e].reverse().findIndex((s) => s.role === "assistant");
  if (n === -1)
    return e;
  const r = e.length - 1 - n, a = e[r], i = {
    ...a,
    thinkingBlocks: [...a.thinkingBlocks ?? [], ...t]
  };
  return e.map((s, o) => o === r ? i : s);
}, Bc = (e) => ({
  id: e.id,
  role: "user",
  content: e.message ?? "",
  status: "complete",
  timestamp: new Date(e.created).getTime(),
  ...e.milestoneId ? { milestoneId: e.milestoneId } : {}
}), Fa = (e) => ({
  id: e.id,
  role: "assistant",
  content: e.message ?? "",
  status: "complete",
  timestamp: new Date(e.created).getTime(),
  ...e.milestoneId ? { milestoneId: e.milestoneId } : {}
}), zc = (e) => ({
  id: e.id,
  content: e.message ?? e.artifactJson ?? "",
  label: e.toolName ?? (e.type === "TOOL_CALL" ? "Tool call" : "Tool result"),
  isComplete: !0,
  kind: "tool",
  toolName: e.toolName,
  toolStatus: e.message?.includes("Error") ? "error" : "success"
}), Lc = async (e, t, n) => {
  let r, a = "", i = [];
  const s = e.fetchConversationMessages;
  if (!s)
    throw new Error("fetchConversationMessages is not available on transport");
  for (; ; ) {
    const o = await s(t, r, n);
    if (a = a || o.name, i = [...i, ...o.messages], o.nextCursor == null)
      return { name: a, messages: i };
    r = o.nextCursor;
  }
}, qa = {
  conversations: [],
  isLoadingConversations: !1,
  isLoadingMessages: !1,
  isLoadingOlderMessages: !1,
  hasOlderMessages: !1,
  currentOldestCursor: null,
  error: null,
  hasMore: !1,
  currentPage: 0
}, $a = (e) => [...e].sort(
  (t, n) => new Date(n.updated ?? n.created).getTime() - new Date(t.updated ?? t.created).getTime()
), Fc = 20, Ua = 100, Ga = async (e, t, n) => {
  if (e.fetchConversations) {
    n({ isLoadingConversations: !0 });
    try {
      const r = await e.fetchConversations(t, Fc);
      n(t === 0 ? { conversations: $a(r.conversations) } : {
        conversations: $a([...te.getState().conversations, ...r.conversations])
      });
      const a = Math.ceil(r.totalItems / r.size);
      n({ hasMore: t + 1 < a, currentPage: t, error: null });
    } catch (r) {
      const a = r instanceof Error ? r.message : "Failed to load conversation history";
      n({ error: a });
    } finally {
      n({ isLoadingConversations: !1 });
    }
  }
}, te = on((e, t) => ({
  transport: null,
  ...qa,
  setTransport: (n) => {
    e({ transport: n });
  },
  showHistory: () => {
    F.getState().setView("history"), e({ error: null });
    const { transport: n } = t();
    n && Ga(n, 0, e);
  },
  hideHistory: () => {
    F.getState().setView("chat");
  },
  selectConversation: async (n) => {
    const { transport: r } = t();
    if (r?.fetchConversationMessages) {
      e({ isLoadingMessages: !0 });
      try {
        const { messages: a, name: i } = await Lc(r, n, Ua), s = [...a].sort(
          (l, c) => new Date(l.created).getTime() - new Date(c.created).getTime()
        ), o = La(s).map(({ thinkingBlocks: l, ...c }) => c);
        G.getState().reset(), F.getState().loadConversation(o, i), G.getState().setConversationId(n), e({
          error: null,
          hasOlderMessages: !1,
          currentOldestCursor: null
        });
      } catch (a) {
        const i = a instanceof Error ? a.message : "Failed to load conversation";
        e({ error: i });
      } finally {
        e({ isLoadingMessages: !1 });
      }
    }
  },
  loadOlderMessages: async () => {
    const { transport: n, hasOlderMessages: r, currentOldestCursor: a, isLoadingOlderMessages: i } = t(), s = G.getState().conversationId;
    if (!(i || !r || !n?.fetchConversationMessages || !s)) {
      e({ isLoadingOlderMessages: !0 });
      try {
        const o = await n.fetchConversationMessages(
          s,
          a ?? void 0,
          Ua
        ), l = [...o.messages].sort(
          (u, d) => new Date(u.created).getTime() - new Date(d.created).getTime()
        ), c = La(l).map(({ thinkingBlocks: u, ...d }) => d);
        F.getState().prependMessages(c), e({
          hasOlderMessages: o.nextCursor != null,
          currentOldestCursor: o.nextCursor ?? null,
          error: null
        });
      } catch (o) {
        const l = o instanceof Error ? o.message : "Failed to load older messages";
        e({ error: l });
      } finally {
        e({ isLoadingOlderMessages: !1 });
      }
    }
  },
  renameConversation: async (n, r) => {
    const { transport: a, conversations: i } = t();
    if (!a?.updateConversation)
      return;
    const s = i.map((o) => o.id === n ? { ...o, name: r } : o);
    e({ error: null, conversations: s });
    try {
      await a.updateConversation(n, { conversationName: r });
    } catch (o) {
      e({ conversations: i });
      const l = o instanceof Error ? o.message : "Failed to rename conversation";
      e({ error: l });
    }
  },
  deleteConversation: async (n) => {
    const { transport: r, conversations: a } = t();
    if (!r?.deleteConversation)
      return;
    G.getState().conversationId === n && (r.unsubscribe(n), G.getState().reset(), F.getState().clearMessages());
    const i = a.filter((s) => s.id !== n);
    e({ error: null, conversations: i });
    try {
      await r.deleteConversation(n);
    } catch (s) {
      e({ conversations: a });
      const o = s instanceof Error ? s.message : "Failed to delete conversation";
      e({ error: o });
    }
  },
  deleteAllConversations: async () => {
    const { transport: n, conversations: r } = t(), a = n?.deleteConversation;
    if (!a || r.length === 0)
      return;
    const i = G.getState().conversationId;
    i && (n.unsubscribe(i), G.getState().reset(), F.getState().clearMessages()), e({ error: null, conversations: [] });
    try {
      await Promise.all(r.map((s) => a(s.id)));
    } catch (s) {
      e({ conversations: r });
      const o = s instanceof Error ? s.message : "Failed to delete all conversations";
      e({ error: o });
    }
  },
  loadMore: () => {
    const { isLoadingConversations: n, hasMore: r, currentPage: a, transport: i } = t();
    !n && r && i && Ga(i, a + 1, e);
  },
  reset: () => {
    e(qa);
  },
  rollback: async (n, r) => {
    const { transport: a } = t();
    if (a?.rollbackConversation)
      try {
        const i = await a.rollbackConversation(n, r);
        i && F.getState().addRollbackMessage(i.message, i.milestoneId);
      } catch (i) {
        const s = i instanceof Error ? i.message : "Failed to restore conversation";
        e({ error: s });
      }
  }
})), qc = "_confirmation_prut5_9", $c = "_actions_prut5_16", Ha = {
  confirmation: qc,
  actions: $c
}, Hr = ({ message: e, onConfirm: t, onCancel: n }) => /* @__PURE__ */ j("div", { role: "alert", className: Ha.confirmation, children: [
  /* @__PURE__ */ y("span", { children: e }),
  /* @__PURE__ */ j("div", { className: Ha.actions, children: [
    /* @__PURE__ */ y(rt, { kind: "danger", size: "sm", onClick: t, children: "Delete" }),
    /* @__PURE__ */ y(rt, { kind: "ghost", size: "sm", onClick: n, children: "Cancel" })
  ] })
] }), Uc = "_conversationItem_1k09c_9", Gc = "_rowContent_1k09c_38", Hc = "_rowActions_1k09c_45", Vc = "_actionIcon_1k09c_58", Kc = "_editRow_1k09c_64", Wc = "_editInput_1k09c_70", Xc = "_rowButton_1k09c_75", Qc = "_active_1k09c_93", Jc = "_activeDot_1k09c_97", Yc = "_timestampRow_1k09c_109", Zc = "_timestamp_1k09c_109", le = {
  conversationItem: Uc,
  rowContent: Gc,
  rowActions: Hc,
  actionIcon: Vc,
  editRow: Kc,
  editInput: Wc,
  rowButton: Xc,
  active: Qc,
  activeDot: Jc,
  timestampRow: Yc,
  timestamp: Zc
}, eu = ({
  conversation: e,
  isActive: t,
  onSelect: n,
  onRename: r,
  onDelete: a
}) => {
  const [i, s] = He("default"), [o, l] = He(e.name), c = Q(null);
  se(() => {
    i === "editing" && (c.current?.focus(), c.current?.select());
  }, [i]);
  const u = M(
    (w) => {
      w.stopPropagation(), l(e.name), s("editing");
    },
    [e.name]
  ), d = M(() => {
    const w = o.trim();
    w && w !== e.name && r(e.id, w), s("default");
  }, [o, e.id, e.name, r]), p = M(() => {
    l(e.name), s("default");
  }, [e.name]), m = M(
    (w) => {
      w.key === "Enter" ? (w.preventDefault(), d()) : w.key === "Escape" && (w.preventDefault(), p());
    },
    [d, p]
  ), h = M((w) => {
    w.stopPropagation(), s("confirming-delete");
  }, []), g = M(() => {
    a(e.id);
  }, [e.id, a]), v = M(() => {
    s("default");
  }, []), k = M(() => {
    n(e.id);
  }, [n, e.id]), f = Fr(new Date(e.updated || e.created).getTime());
  return i === "editing" ? /* @__PURE__ */ j("div", { className: le.conversationItem, "data-editing": !0, children: [
    /* @__PURE__ */ y("span", { className: le.timestamp, children: f }),
    /* @__PURE__ */ j("div", { className: le.editRow, children: [
      /* @__PURE__ */ y(
        Oi,
        {
          id: `rename-${e.id}`,
          ref: c,
          size: "sm",
          labelText: "Conversation name",
          hideLabel: !0,
          enableCounter: !0,
          maxCount: 100,
          value: o,
          onChange: (w) => l(w.target.value),
          onKeyDown: m,
          className: le.editInput
        }
      ),
      /* @__PURE__ */ y(at, { kind: "ghost", size: "sm", label: "Confirm", onClick: d, className: le.actionIcon, children: /* @__PURE__ */ y($i, {}) }),
      /* @__PURE__ */ y(at, { kind: "ghost", size: "sm", label: "Cancel", onClick: p, className: le.actionIcon, children: /* @__PURE__ */ y(Ui, {}) })
    ] })
  ] }) : i === "confirming-delete" ? /* @__PURE__ */ y("div", { className: le.conversationItem, "data-confirming-delete": !0, children: /* @__PURE__ */ y(
    Hr,
    {
      message: "Delete this conversation?",
      onConfirm: g,
      onCancel: v
    }
  ) }) : /* @__PURE__ */ y("div", { className: `${le.conversationItem} ${t ? le.active : ""}`, children: /* @__PURE__ */ j("div", { className: le.rowContent, children: [
    /* @__PURE__ */ j("button", { type: "button", className: le.rowButton, onClick: k, children: [
      /* @__PURE__ */ j("span", { className: le.timestampRow, children: [
        t && /* @__PURE__ */ y("span", { className: le.activeDot, "data-testid": "active-indicator", "aria-label": "Active conversation" }),
        /* @__PURE__ */ y("span", { className: le.timestamp, children: f })
      ] }),
      /* @__PURE__ */ y("span", { children: e.name || "Untitled conversation" })
    ] }),
    /* @__PURE__ */ j("div", { className: le.rowActions, children: [
      /* @__PURE__ */ y(
        at,
        {
          kind: "ghost",
          size: "sm",
          label: "Rename",
          autoAlign: !0,
          onClick: u,
          className: le.actionIcon,
          children: /* @__PURE__ */ y(Gi, {})
        }
      ),
      /* @__PURE__ */ y(
        at,
        {
          kind: "ghost",
          size: "sm",
          label: "Delete",
          autoAlign: !0,
          onClick: h,
          className: le.actionIcon,
          children: /* @__PURE__ */ y(Hi, {})
        }
      )
    ] })
  ] }) });
}, tu = "_panel_bjoxv_9", nu = "_conversationList_bjoxv_18", au = "_emptyState_bjoxv_27", ru = "_emptyStateCard_bjoxv_31", iu = "_emptyStateIcon_bjoxv_42", su = "_emptyStateText_bjoxv_58", ou = "_loadMoreContainer_bjoxv_64", lu = "_loadingMoreContainer_bjoxv_70", cu = "_loadingRow_bjoxv_77", uu = "_errorNotification_bjoxv_84", du = "_skeletonItem_bjoxv_88", pu = "_footer_bjoxv_96", mu = "_footerText_bjoxv_106", ke = {
  panel: tu,
  conversationList: nu,
  emptyState: au,
  emptyStateCard: ru,
  emptyStateIcon: iu,
  emptyStateText: su,
  loadMoreContainer: ou,
  loadingMoreContainer: lu,
  loadingRow: cu,
  errorNotification: uu,
  skeletonItem: du,
  footer: pu,
  footerText: mu
}, hu = ({
  conversations: e,
  activeConversationId: t,
  onSelectConversation: n,
  onRenameConversation: r,
  onDeleteConversation: a,
  onDeleteAllConversations: i,
  isLoadingConversations: s,
  isOpeningConversation: o,
  error: l,
  hasMore: c,
  onLoadMore: u
}) => {
  const [d, p] = He(!1), m = s && e.length > 0;
  return /* @__PURE__ */ j("div", { className: ke.panel, children: [
    /* @__PURE__ */ j("div", { className: ke.conversationList, children: [
      l && /* @__PURE__ */ y(
        pr,
        {
          className: ke.errorNotification,
          kind: "error",
          lowContrast: !0,
          title: "Unable to load conversation history",
          subtitle: l,
          hideCloseButton: !0
        }
      ),
      s && e.length === 0 ? /* @__PURE__ */ y(fu, {}) : e.length === 0 ? /* @__PURE__ */ y("div", { className: ke.emptyState, children: /* @__PURE__ */ j("div", { className: ke.emptyStateCard, children: [
        /* @__PURE__ */ y("div", { className: ke.emptyStateIcon, children: /* @__PURE__ */ y(Ur, { icon: $r }) }),
        /* @__PURE__ */ y("span", { className: ke.emptyStateText, children: "Chat history will appear here after your first chat." })
      ] }) }) : /* @__PURE__ */ j(yn, { children: [
        e.map((h) => /* @__PURE__ */ y(
          eu,
          {
            conversation: h,
            isActive: h.id === t,
            onSelect: n,
            onRename: r,
            onDelete: a
          },
          h.id
        )),
        m && /* @__PURE__ */ y("div", { className: ke.loadingMoreContainer, children: /* @__PURE__ */ y(Va, { label: "Loading more conversations" }) }),
        o && /* @__PURE__ */ y("div", { className: ke.loadingMoreContainer, children: /* @__PURE__ */ y(Va, { label: "Opening conversation" }) }),
        c && /* @__PURE__ */ y("div", { className: ke.loadMoreContainer, children: /* @__PURE__ */ y(
          rt,
          {
            kind: "ghost",
            size: "sm",
            onClick: u,
            disabled: s || o,
            children: "Load more"
          }
        ) })
      ] })
    ] }),
    /* @__PURE__ */ y("div", { className: ke.footer, children: d ? /* @__PURE__ */ y(
      Hr,
      {
        message: "Delete all conversations?",
        onConfirm: () => {
          i(), p(!1);
        },
        onCancel: () => p(!1)
      }
    ) : /* @__PURE__ */ j(yn, { children: [
      /* @__PURE__ */ y("span", { className: ke.footerText, children: "Chats are saved up to 90 days." }),
      /* @__PURE__ */ y(
        rt,
        {
          kind: "ghost",
          size: "sm",
          disabled: e.length === 0,
          onClick: () => p(!0),
          children: "Delete all"
        }
      )
    ] }) })
  ] });
}, fu = () => /* @__PURE__ */ y(yn, { children: Array.from({ length: 5 }, (e, t) => /* @__PURE__ */ j("div", { className: ke.skeletonItem, children: [
  /* @__PURE__ */ y(ia, { heading: !0, width: "60%" }),
  /* @__PURE__ */ y(ia, { width: "30%" })
] }, t)) }), Va = ({ label: e }) => /* @__PURE__ */ j("div", { className: ke.loadingRow, role: "status", "aria-live": "polite", children: [
  /* @__PURE__ */ y(Pi, { withOverlay: !1, small: !0 }),
  /* @__PURE__ */ y("span", { className: "cds--type-body-compact-01", children: e })
] }), Ka = ({
  onSendMessage: e,
  onStopGeneration: t,
  onResetConversation: n,
  onLoadConversation: r,
  isBusy: a = !1,
  isDisabled: i = !1,
  isOpen: s,
  emptyStateTitle: o,
  emptyStateDescription: l,
  suggestions: c,
  className: u,
  contextItems: d,
  onRemoveContext: p,
  onSeeVersion: m
}) => {
  const h = F((S) => S.messages), g = F((S) => S.clearMessages), v = F((S) => S.conversationTitle), k = F((S) => S.view), f = G((S) => S.conversationId), w = te((S) => !!S.transport?.fetchConversations), E = te((S) => !!S.transport?.rollbackConversation), x = te((S) => S.rollback), b = te((S) => S.conversations), I = te((S) => S.isLoadingConversations), C = te((S) => S.isLoadingMessages), O = te((S) => S.error), q = te((S) => S.hasMore), A = te((S) => S.showHistory), T = te((S) => S.hideHistory), _ = te((S) => S.selectConversation), P = te((S) => S.renameConversation), Oe = te((S) => S.deleteConversation), $ = te((S) => S.deleteAllConversations), D = te((S) => S.loadMore), ze = te((S) => S.hasOlderMessages), Pe = te((S) => S.isLoadingOlderMessages), ne = te((S) => S.loadOlderMessages), B = r ?? _, Le = M(() => {
    g(), n?.();
  }, [g, n]), et = M(
    (S) => {
      f && x(f, S);
    },
    [f, x]
  ), tt = M(() => {
    k === "history" ? T() : A();
  }, [k, A, T]), Ie = M(
    (S) => {
      e(S.label);
    },
    [e]
  );
  return /* @__PURE__ */ j(Qi, { className: `${Ot.container} ${u ?? ""}`, showWorkspace: k === "history", children: [
    /* @__PURE__ */ y("div", { slot: "header", children: /* @__PURE__ */ y(
      fc,
      {
        onReset: i ? void 0 : Le,
        onShowHistory: i ? void 0 : w ? tt : void 0,
        conversationTitle: v ?? void 0,
        view: k,
        isBusy: a
      }
    ) }),
    /* @__PURE__ */ y("div", { slot: "messages", className: Ot.messagesSlot, children: /* @__PURE__ */ y(
      nc,
      {
        messages: h,
        emptyStateTitle: o,
        emptyStateDescription: l,
        suggestions: i ? void 0 : c,
        onSuggestionClick: Ie,
        onRollback: E ? et : void 0,
        isBusy: a,
        onSeeVersion: m,
        hasOlderMessages: ze,
        isLoadingOlderMessages: Pe,
        onLoadOlderMessages: ne
      }
    ) }),
    /* @__PURE__ */ y("div", { slot: "workspace", className: Ot.workspaceSlot, children: /* @__PURE__ */ y(
      hu,
      {
        conversations: b,
        activeConversationId: f,
        onSelectConversation: B,
        onRenameConversation: P,
        onDeleteConversation: Oe,
        onDeleteAllConversations: $,
        isLoadingConversations: I,
        isOpeningConversation: C,
        error: O,
        hasMore: q,
        onLoadMore: D
      }
    ) }),
    /* @__PURE__ */ y("div", { slot: "input", children: /* @__PURE__ */ j("div", { className: Ot.inputBlock, children: [
      /* @__PURE__ */ y(bc, { items: d, onRemoveItem: p }),
      /* @__PURE__ */ y(
        ll,
        {
          onSend: e,
          onStop: t,
          isBusy: a,
          isDisabled: i,
          isOpen: s
        }
      )
    ] }) })
  ] });
}, gu = 6e4, yu = 5, bu = 50;
class vu extends Error {
  constructor(t = "Query was cancelled due to a new query being submitted") {
    super(t), this.name = "QueryCancelledError";
  }
}
class ku {
  executor = null;
  pendingQuery = null;
  conversationRef = null;
  setConversationRef(t) {
    this.conversationRef = t;
  }
  getConversationRef() {
    return this.conversationRef;
  }
  setExecutor(t) {
    this.executor = t;
  }
  getExecutor() {
    return this.executor;
  }
  setPendingQuery(t) {
    this.pendingQuery && t !== null && (clearTimeout(this.pendingQuery.timeout), this.pendingQuery.reject(new vu())), this.pendingQuery = t;
  }
  getPendingQuery() {
    return this.pendingQuery;
  }
  clearPendingQuery() {
    this.pendingQuery && clearTimeout(this.pendingQuery.timeout), this.pendingQuery = null;
  }
  isPendingQueryMatch(t) {
    return this.pendingQuery?.query === t;
  }
}
const je = new ku(), wu = (e) => ({
  content: "",
  source_url: e.source_url ?? "",
  title: e.title ?? "",
  source_type: e.source_type ?? "document"
}), xu = (e) => ({
  content: "",
  source_url: e.source_url ?? "",
  title: e.title ?? "",
  source_type: e.source_type ?? "document"
}), Eu = (e) => {
  const t = /* @__PURE__ */ new Set();
  return e.filter((n) => {
    const r = t.has(n.source_url);
    return t.add(n.source_url), !r;
  });
};
function Wa(e) {
  je.setConversationRef(e);
}
function Xa(e) {
  je.setExecutor(e);
}
function _u() {
  return je.getExecutor();
}
function Iu() {
  return (e) => {
    const t = je.getPendingQuery();
    if (!t) return;
    const n = e.conversation?.length ? e.conversation[e.conversation.length - 1] : void 0, r = e.question ?? n?.question;
    if (r && r !== t.query)
      return;
    const a = e.answer ?? n?.answer ?? "", i = e.sources ?? n?.sources ?? [], s = (l) => {
      const c = je.getPendingQuery();
      c && (clearTimeout(c.timeout), je.setPendingQuery(null), c.resolve({
        answer: a,
        sources: Eu(l)
      }));
    };
    if (i.length > 0) {
      const l = i.map((c) => wu(c));
      s(l);
      return;
    }
    const o = (l) => {
      if (!je.getPendingQuery()) return;
      const u = je.getConversationRef();
      if (u?.current) {
        const d = u.current.getLatest();
        if (d?.sources && d.sources.length > 0) {
          const p = d.sources.map((m) => xu(m));
          s(p);
          return;
        }
      }
      l > 0 ? setTimeout(() => o(l - 1), bu) : s([]);
    };
    o(yu);
  };
}
function Cu(e, t = gu) {
  return (n) => new Promise((r, a) => {
    const i = setTimeout(() => {
      je.isPendingQueryMatch(n) && (je.clearPendingQuery(), a(new Error("Request timed out")));
    }, t);
    je.setPendingQuery({
      resolve: r,
      reject: a,
      query: n,
      timeout: i
    }), e(n);
  });
}
function Tu({ children: e }) {
  const { submitQuery: t, conversation: n } = rl(), r = Q(n);
  return r.current = n, se(() => {
    Wa(r);
    const a = Cu(t);
    return Xa(a), () => {
      Xa(null), Wa(null);
    };
  }, [t]), e;
}
const Qf = ({
  onSendMessage: e,
  kapaAiIntegrationId: t,
  onStopGeneration: n,
  onResetConversation: r,
  isBusy: a = !1,
  isDisabled: i = !1,
  isOpen: s,
  emptyStateTitle: o,
  emptyStateDescription: l,
  suggestions: c,
  className: u,
  contextItems: d,
  onRemoveContext: p,
  onSeeVersion: m
}) => {
  const h = {
    onSendMessage: e,
    onStopGeneration: n,
    onResetConversation: r,
    isBusy: a,
    isDisabled: i,
    isOpen: s,
    emptyStateTitle: o,
    emptyStateDescription: l,
    suggestions: c,
    className: u,
    contextItems: d,
    onRemoveContext: p,
    onSeeVersion: m
  }, g = dr(
    () => ({
      askAI: {
        onQuerySubmit: () => {
        },
        onAnswerGenerationCompleted: Iu()
      }
    }),
    []
  ), v = t ? /* @__PURE__ */ y(al, { integrationId: t, callbacks: g, userTrackingMode: "none", children: /* @__PURE__ */ y(Tu, { children: /* @__PURE__ */ y(Ka, { ...h }) }) }) : /* @__PURE__ */ y(Ka, { ...h });
  return /* @__PURE__ */ y(Di, { theme: "g10", className: Ot.themeWrapper, children: v });
}, Su = "_wrapper_2p3wu_9", Ru = "_wrapperOpen_2p3wu_22", Nu = "_panel_2p3wu_26", hn = {
  wrapper: Su,
  wrapperOpen: Ru,
  panel: Nu
}, Au = () => ({
  activeCount: 0,
  rootSnapshot: null,
  hostRegistry: /* @__PURE__ */ new Map(),
  mediaWatcher: null,
  mode: "sidecar",
  activeWidth: 416
}), Mu = () => {
  const e = { current: Au() }, t = () => e.current, n = (a) => {
    e.current = { ...e.current, ...a };
  };
  return { getState: t, setState: n, updateHostRegistry: (a) => {
    const i = t();
    n({ hostRegistry: a(i.hostRegistry) });
  } };
}, J = Mu(), Vr = "data-copilot-chat-layout", Tn = "data-copilot-chat-host", tn = "--copilot-chat-sidecar-width", nn = "--copilot-chat-panel-z-index", an = "--copilot-chat-offset-top", Ye = () => typeof document < "u" && typeof window < "u", Ou = (e) => {
  if (!Ye() || !e)
    return 0;
  const t = document.querySelector(e);
  return t instanceof HTMLElement ? t.offsetHeight : 0;
}, Pu = (e) => {
  if (!Ye())
    return [];
  if (e) {
    const t = document.querySelectorAll(e), n = Array.from(t).filter(
      (r) => r instanceof HTMLElement
    );
    return n.length > 0 ? n : [document.body];
  }
  return [document.body];
}, Kr = (e) => {
  J.getState().hostRegistry.forEach((n, r) => r.setAttribute(Tn, e));
}, Du = (e, t, n) => {
  if (!Ye())
    return;
  const r = document.documentElement;
  J.getState().rootSnapshot || J.setState({
    rootSnapshot: {
      width: r.style.getPropertyValue(tn),
      zIndex: r.style.getPropertyValue(nn),
      topOffset: r.style.getPropertyValue(an)
    }
  }), J.setState({ activeWidth: e }), r.style.setProperty(tn, `${e}px`), r.style.setProperty(nn, `${t}`), r.style.setProperty(an, `${n}px`), Kr(J.getState().mode);
}, ju = () => {
  if (!Ye())
    return;
  const e = J.getState();
  if (!e.rootSnapshot)
    return;
  const t = document.documentElement, { width: n, zIndex: r, topOffset: a } = e.rootSnapshot;
  n ? t.style.setProperty(tn, n) : t.style.removeProperty(tn), r ? t.style.setProperty(nn, r) : t.style.removeProperty(nn), a ? t.style.setProperty(an, a) : t.style.removeProperty(an), J.setState({ rootSnapshot: null, activeWidth: 416 });
}, Bu = (e) => {
  const t = J.getState(), n = t.hostRegistry.get(e) ?? { count: 0 };
  return J.updateHostRegistry((r) => {
    const a = new Map(r);
    return a.set(e, { count: n.count + 1 }), a;
  }), e.setAttribute(Tn, t.mode), () => {
    const a = J.getState().hostRegistry.get(e);
    if (!a)
      return;
    const i = a.count - 1;
    if (i <= 0) {
      e.removeAttribute(Tn), J.updateHostRegistry((s) => {
        const o = new Map(s);
        return o.delete(e), o;
      });
      return;
    }
    J.updateHostRegistry((s) => {
      const o = new Map(s);
      return o.set(e, { count: i }), o;
    });
  };
}, Qa = (e) => {
  Ye() && (J.setState({ mode: e }), document.body.setAttribute(Vr, e), Kr(e));
}, zu = () => {
  Ye() && (document.body.removeAttribute(Vr), J.setState({ mode: "sidecar" }));
}, Lu = (e) => {
  if (!Ye() || typeof window.matchMedia != "function")
    return Qa("sidecar"), () => {
    };
  const t = window.matchMedia(`(max-width: ${e}px)`), n = (a) => {
    Qa(a ? "bottom-sheet" : "sidecar");
  };
  n(t.matches);
  const r = (a) => n(a.matches);
  return t.addEventListener("change", r), () => t.removeEventListener("change", r);
}, Fu = (e) => {
  if (!Ye())
    return;
  const t = J.getState();
  if (t.mediaWatcher && t.mediaWatcher.breakpoint === e)
    return;
  t.mediaWatcher?.dispose();
  const n = Lu(e);
  J.setState({ mediaWatcher: { breakpoint: e, dispose: n } });
}, qu = () => {
  J.getState().mediaWatcher?.dispose(), J.setState({ mediaWatcher: null }), zu();
}, $u = ({
  width: e,
  zIndex: t,
  workareaSelector: n,
  headerSelector: r,
  responsiveBreakpoint: a
}) => {
  if (!Ye())
    return () => {
    };
  const i = Pu(n), s = Ou(r);
  Du(e, t, s), Fu(a);
  const o = i.map((c) => Bu(c)), l = J.getState();
  return J.setState({ activeCount: l.activeCount + 1 }), () => {
    o.forEach((d) => d());
    const c = J.getState(), u = Math.max(c.activeCount - 1, 0);
    J.setState({ activeCount: u }), u === 0 && (qu(), ju());
  };
}, Uu = ({
  enabled: e,
  width: t,
  zIndex: n,
  workareaSelector: r,
  headerSelector: a,
  responsiveBreakpoint: i
}) => {
  ur(() => {
    if (!e)
      return;
    const s = $u({
      width: t,
      zIndex: n,
      workareaSelector: r,
      headerSelector: a,
      responsiveBreakpoint: i
    });
    return () => s();
  }, [e, t, n, r, a, i]);
}, Gu = "._container_db17r_9{display:flex;flex-wrap:wrap;align-items:center;gap:var(--cds-spacing-02);padding:var(--cds-spacing-03) var(--cds-spacing-05);background-color:var(--cds-layer-01)}._tag_db17r_18{max-inline-size:100%;min-inline-size:0}", Hu = "._panel_bjoxv_9{display:flex;flex-direction:column;flex:1;height:100%;min-height:0;overflow:hidden}._conversationList_bjoxv_18{display:flex;flex-direction:column;flex:1;overflow-x:hidden;overflow-y:auto;padding:0}._emptyState_bjoxv_27{padding:var(--cds-spacing-05)}._emptyStateCard_bjoxv_31{display:flex;flex-direction:column;align-items:center;gap:var(--cds-spacing-05);padding:var(--cds-spacing-07) var(--cds-spacing-06);background:var(--cds-layer-01);border-radius:var(--cds-spacing-05);text-align:center}._emptyStateIcon_bjoxv_42{display:flex;align-items:center;justify-content:center;width:var(--cds-spacing-09);height:var(--cds-spacing-09);border-radius:50%;background:var(--cds-layer-02);color:var(--cds-text-secondary)}._emptyStateIcon_bjoxv_42 svg{width:var(--cds-spacing-06);height:var(--cds-spacing-06)}._emptyStateText_bjoxv_58{font-size:var(--cds-body-compact-01-font-size);color:var(--cds-text-secondary);line-height:var(--cds-body-compact-01-line-height)}._loadMoreContainer_bjoxv_64{display:flex;justify-content:center;padding:var(--cds-spacing-03) 0}._loadingMoreContainer_bjoxv_70{display:flex;align-items:center;justify-content:center;padding:var(--cds-spacing-05) 0}._loadingRow_bjoxv_77{display:inline-flex;align-items:center;justify-content:center;gap:var(--cds-spacing-03)}._errorNotification_bjoxv_84{margin:var(--cds-spacing-03)}._skeletonItem_bjoxv_88{display:flex;flex-direction:column;gap:var(--cds-spacing-02);padding:var(--cds-spacing-03) var(--cds-spacing-05);border-block-end:1px solid var(--cds-border-subtle-01)}._footer_bjoxv_96{display:flex;align-items:center;justify-content:space-between;flex-shrink:0;margin-block-start:auto;padding:var(--cds-spacing-03) var(--cds-spacing-05);border-block-start:1px solid var(--cds-border-subtle-01)}._footerText_bjoxv_106{font-size:var(--cds-helper-text-02-font-size);color:var(--cds-text-secondary)}", Vu = "._conversationItem_1k09c_9{display:flex;flex-direction:column;gap:var(--cds-spacing-02);background-color:inherit;color:inherit;text-decoration:none;text-align:start;cursor:pointer;inline-size:100%;border:0;border-radius:0;border-block-end:1px solid var(--cds-border-subtle-01);padding:var(--cds-spacing-03) var(--cds-spacing-05)}._conversationItem_1k09c_9:hover{background-color:var(--cds-layer-hover-01)}._conversationItem_1k09c_9:hover,._conversationItem_1k09c_9:link,._conversationItem_1k09c_9:focus,._conversationItem_1k09c_9:active,._conversationItem_1k09c_9:visited{color:inherit;text-decoration:none}._rowContent_1k09c_38{display:flex;align-items:center;gap:var(--cds-spacing-03);inline-size:100%}._rowActions_1k09c_45{display:flex;gap:var(--cds-spacing-01);flex-shrink:0;opacity:0;transition:opacity .12s ease}._conversationItem_1k09c_9:hover ._rowActions_1k09c_45,._conversationItem_1k09c_9:focus-within ._rowActions_1k09c_45{opacity:1}._actionIcon_1k09c_58{min-block-size:unset;block-size:1.5rem;inline-size:1.5rem}._editRow_1k09c_64{display:flex;align-items:flex-end;gap:var(--cds-spacing-02)}._editInput_1k09c_70{flex:1;min-inline-size:0}._rowButton_1k09c_75{display:flex;flex-direction:column;align-items:flex-start;gap:var(--cds-spacing-02);flex:1;min-inline-size:0;padding:0;border:0;background:none;text-align:start;color:var(--cds-text-primary);font-family:inherit;font-size:inherit;line-height:inherit;cursor:pointer}._active_1k09c_93{background-color:var(--cds-layer-selected-01)}._activeDot_1k09c_97{position:absolute;inset-block-start:50%;inset-inline-start:calc(-1 * var(--cds-spacing-03));translate:0 -50%;display:block;inline-size:.375rem;block-size:.375rem;border-radius:50%;background-color:var(--cds-support-success)}._timestampRow_1k09c_109{position:relative;display:inline-flex;align-items:center}._timestamp_1k09c_109{font-size:var(--cds-helper-text-01-font-size);font-weight:var(--cds-label-01-font-weight);color:var(--cds-text-secondary)}", Ku = "._confirmation_prut5_9{display:flex;align-items:center;gap:var(--cds-spacing-03);inline-size:100%}._actions_prut5_16{display:flex;gap:var(--cds-spacing-03);margin-inline-start:auto}", Wu = '._themeWrapper_neun4_9{display:flex;flex-direction:column;flex:1;min-height:0;height:100%}._container_neun4_17{--cds-chat-shell-background: var(--cds-background);display:flex;flex-direction:column;flex:1;min-height:0;height:100%;background-color:var(--cds-background);font-family:var(--cds-font-family-sans)}._messagesSlot_neun4_29,._workspaceSlot_neun4_36{display:flex;flex-direction:column;height:100%;min-height:0}._inputSlotNoBorder_neun4_43>div{border-block-start:none}._inputBlock_neun4_47{border-block-start:1px solid var(--cds-border-subtle-00);position:relative}._inputBlock_neun4_47:focus-within:after{content:"";position:absolute;inset:0;border:2px solid var(--cds-focus);pointer-events:none}', Xu = "._wrapper_2p3wu_9{position:fixed;inset-block-start:var(--copilot-chat-offset-top);inset-inline-end:0;inset-block-end:0;inline-size:var(--copilot-chat-sidecar-width);z-index:var(--copilot-chat-panel-z-index);transform:translate(100%);transition:transform .3s ease-in-out;display:flex;flex-direction:column}._wrapperOpen_2p3wu_22{transform:translate(0)}._panel_2p3wu_26{display:flex;flex-direction:column;block-size:100%;background-color:var(--cds-background);border-inline-start:1px solid var(--cds-border-subtle-00)}", Qu = ":root{--copilot-chat-sidecar-width: 400px;--copilot-chat-panel-z-index: 9999;--copilot-chat-transition: var(--cds-duration-moderate-01) cubic-bezier(.2, 0, .38, .9);--copilot-chat-offset-top: 0}body[data-copilot-chat-layout=sidecar][data-copilot-chat-host],[data-copilot-chat-host]{padding-inline-end:var(--copilot-chat-sidecar-width);transition:padding-inline-end var(--copilot-chat-transition);box-sizing:border-box}body[data-copilot-chat-layout=bottom-sheet]{padding-inline-end:0}", Ja = "copilot-chat-sidecar-styles", Ju = /* @__PURE__ */ Object.assign({
  "../ContextBar/ContextBar.module.css": Gu,
  "../ConversationHistoryPanel/ConversationHistoryPanel.module.css": Hu,
  "../ConversationHistoryPanel/ConversationHistoryRow.module.css": Vu,
  "../ConversationHistoryPanel/DeleteConfirmation.module.css": Ku,
  "./copilotChat.module.css": Wu,
  "./copilotSidecar.module.css": Xu
}), Yu = [Qu, ...Object.values(Ju)].join(`
`), Zu = () => {
  if (typeof document > "u" || document.getElementById(Ja))
    return;
  const t = document.createElement("style");
  t.id = Ja, t.textContent = Yu, document.head.appendChild(t);
}, Jf = ({ children: e, workareaSelector: t, headerSelector: n, zIndex: r = 12 }) => {
  const a = xt((s) => s.isOpen);
  se(() => {
    Zu();
  }, []), Uu({
    enabled: a,
    width: 400,
    zIndex: r,
    workareaSelector: t,
    headerSelector: n,
    responsiveBreakpoint: 960
  });
  const i = M(() => {
    xt.getState().close();
  }, []);
  return /* @__PURE__ */ y("div", { className: `${hn.wrapper} ${a ? hn.wrapperOpen : ""}`, children: /* @__PURE__ */ y("div", { className: hn.panel, children: e({ onMinimize: i, isOpen: a }) }) });
}, Yf = () => {
  xt.getState().open();
}, Zf = () => {
  xt.getState().close();
}, eg = () => {
  xt.getState().toggle();
}, tg = () => xt((e) => e.isOpen), ed = (e) => e.replace(/_/g, " "), td = (e) => Ee.getDisplayName(e) || void 0, Wr = (e, t, n) => {
  const r = t ? td(t) : void 0, a = t ? ed(t) : void 0;
  switch (e) {
    case H.THINKING:
      return "Thinking...";
    case H.EXECUTION_PLAN:
      return "Planning...";
    case H.TOOL_PLANNING:
      return r ? `${r}...` : a ? `Planning ${a}...` : "Planning...";
    case H.TOOL_INVOKE:
    case H.EXTERNAL_TOOL_CALL:
      return r ? `${r}...` : a ? `Running ${a}...` : "Running tool...";
    case H.TOOL_RESULT:
      return n === oe.ERROR ? r ? `${r} failed — retrying...` : a ? `${a} failed — retrying...` : "Processing results..." : r ? `${r}...` : a ? `Processing ${a} results...` : "Processing results...";
    case H.EXECUTION_COMPLETE:
      return "Completing...";
    case H.ERROR:
      return "Error";
    default:
      return "Thinking...";
  }
}, Xr = (e) => e.type === H.THINKING && (e.status === oe.IN_PROGRESS || e.status === oe.COMPLETED) && !!e.content, nd = (e) => e.type === H.TOOL_PLANNING && (e.status === oe.IN_PROGRESS || e.status === oe.COMPLETED), ad = (e) => e.type === H.TOOL_RESULT && (e.status === oe.IN_PROGRESS || e.status === oe.COMPLETED), Qr = (e) => e.type === H.TOOL_INVOKE || e.type === H.EXTERNAL_TOOL_CALL, rd = (e) => e.type === H.EXECUTION_COMPLETE, id = (e) => e.status === oe.ERROR, sd = (e) => e.status === oe.IN_PROGRESS && !!e.type, od = (e) => Xr(e) || nd(e) || Qr(e) || ad(e), ld = (e) => e.type === H.TOOL_RESULT && e.toolName === "write_artifact" && e.status === oe.COMPLETED, cd = (e) => e.type === H.TOOL_RESULT && e.status === oe.COMPLETED && e.toolName === "layout_bpmn_xml", ud = (e) => e.type === H.TOOL_RESULT && e.status === oe.COMPLETED && e.toolName === "apply_element_template", ng = (e) => ld(e) || cd(e) || ud(e), dd = () => `thinking-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`, pd = (e, t) => e.startsWith(t) ? e.slice(t.length) : (t.length > 0 ? `
` : "") + e, md = (e) => {
  const n = {
    [H.TOOL_PLANNING]: "Planning",
    [H.TOOL_INVOKE]: "Executing",
    [H.TOOL_RESULT]: "Result"
  }[e.type] || e.type, r = e.toolName ? ` [${e.toolName}]` : "", a = e.content || e.toolResult || "";
  return `**${n}${r}**: ${a}`;
}, fn = (e, t) => {
  const { addThinkingBlock: n, appendToThinkingBlock: r, completeThinkingBlock: a, completeAllThinkingBlocks: i } = t;
  switch (e.type) {
    case "ADD_BLOCK":
      n(e.messageId, e.blockId, e.label), e.content && r(e.messageId, e.blockId, e.content);
      break;
    case "APPEND_BLOCK":
      e.content && r(e.messageId, e.blockId, e.content);
      break;
    case "COMPLETE_BLOCK":
      a(e.messageId, e.blockId);
      break;
    case "COMPLETE_ALL":
      i(e.messageId);
      break;
  }
}, hd = (e, t) => ({
  contentType: Ee.getContentType(e),
  resultString: typeof t == "string" ? t : JSON.stringify(t)
}), fd = (e, t) => sd(e) ? t(e.type, e.toolName) : void 0, gd = (e, t, n, r, a, i, s) => {
  if (!od(e))
    return { newBlockId: n, newContent: r };
  const o = Xr(e) ? e.content || "" : md(e);
  if (!o)
    return { newBlockId: n, newContent: r };
  const l = !n, c = l ? dd() : n, u = a || i || `Thinking (${s + 1})...`, d = pd(o, l ? "" : r);
  return {
    action: l ? { type: "ADD_BLOCK", messageId: t, blockId: c, label: u, content: d } : { type: "APPEND_BLOCK", messageId: t, blockId: c, label: "", content: d },
    newBlockId: c,
    newContent: o
  };
}, yd = ({
  event: e,
  messageId: t,
  currentBlockId: n,
  lastThinkingContent: r,
  lastStatusLabel: a,
  blockCount: i,
  accumulatedContent: s,
  getStatusLabel: o
}) => {
  if (!t)
    return {
      newBlockId: n,
      newThinkingContent: r,
      newAccumulatedContent: s,
      newBlockCount: i
    };
  if (id(e) && e.type !== H.TOOL_RESULT)
    return {
      intermediateError: e.content || "Copilot agent error",
      thinkingAction: n ? { type: "COMPLETE_BLOCK", messageId: t, blockId: n, label: "", content: "" } : void 0,
      newBlockId: null,
      newThinkingContent: "",
      newAccumulatedContent: s,
      newBlockCount: i
    };
  const l = fd(e, o), {
    action: c,
    newBlockId: u,
    newContent: d
  } = gd(
    e,
    t,
    n,
    r,
    l || "",
    a,
    i
  ), p = c?.type === "ADD_BLOCK" ? i + 1 : i, m = s;
  if (Qr(e)) {
    const h = l || o(e.type, e.toolName);
    return n && c ? {
      statusLabel: h,
      thinkingAction: {
        type: "COMPLETE_BLOCK",
        messageId: t,
        blockId: n,
        label: "",
        content: ""
      },
      newBlockId: null,
      newThinkingContent: "",
      newAccumulatedContent: m,
      newBlockCount: p
    } : {
      statusLabel: h,
      thinkingAction: c,
      newBlockId: null,
      newThinkingContent: "",
      newAccumulatedContent: m,
      newBlockCount: p
    };
  }
  if (rd(e)) {
    const h = e.content ?? "";
    return {
      statusLabel: l,
      thinkingAction: {
        type: "COMPLETE_ALL",
        messageId: t,
        blockId: "",
        label: "",
        content: ""
      },
      finalContent: h,
      newBlockId: null,
      newThinkingContent: "",
      newAccumulatedContent: h,
      newBlockCount: p
    };
  }
  return {
    statusLabel: l,
    thinkingAction: c,
    newBlockId: u,
    newThinkingContent: d,
    newAccumulatedContent: m,
    newBlockCount: p
  };
}, vt = (e) => {
  const t = G.getState().currentMessageId;
  if (!t) return;
  const r = [...F.getState().messages.find((a) => a.id === t)?.thinkingBlocks ?? []].reverse().find((a) => a.kind === "tool" && a.toolStatus === "processing" && a.toolName === e);
  r && F.getState().updateToolBlockStatus(t, r.id, "error");
}, Ya = async (e, t, n, r) => {
  const a = G.getState().conversationId;
  a && await e.sendToolResult({
    conversationId: a,
    type: "TOOL_RESULT",
    toolName: t,
    toolCallId: r,
    toolResult: JSON.stringify({ error: n }),
    contentType: "TEXT"
  });
}, bd = ({ onToolInvoke: e, transport: t }) => {
  const n = Q(e), r = Q(t);
  n.current = e, r.current = t;
  const a = M((s, o) => {
    vt(s);
  }, []);
  return {
    executeToolAndSendResult: M(
      async (s, o, l) => {
        const c = Ee.has(s) ? Ee.getHandler(s) : void 0, u = n.current;
        if (c === void 0 && u === void 0) {
          const p = `Tool "${s}" invoked but no handler provided.`;
          vt(s), await Ya(r.current, s, p, l);
          return;
        }
        try {
          const m = c ? await c(o, (k, f) => {
            throw vt(k), f;
          }) : await u?.(s, o), { contentType: h, resultString: g } = Ee.has(s) ? hd(s, m) : { contentType: "TEXT", resultString: String(m) }, v = G.getState().conversationId;
          v && await r.current.sendToolResult({
            conversationId: v,
            type: "TOOL_RESULT",
            toolName: s,
            toolCallId: l,
            toolResult: g,
            contentType: h
          });
        } catch (p) {
          const m = p instanceof Error ? p.message : "Tool execution failed";
          vt(s), await Ya(r.current, s, m, l);
        }
      },
      []
    ),
    handleToolExecutionError: a
  };
}, vd = () => `tool-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`, kd = ({
  getStatusLabel: e,
  getMessageContext: t,
  onUpdateMessageContext: n
}) => {
  const r = F((f) => f.updateMessageStatusLabel), a = F((f) => f.addThinkingBlock), i = F((f) => f.appendToThinkingBlock), s = F((f) => f.completeThinkingBlock), o = F((f) => f.completeAllThinkingBlocks), l = F((f) => f.setMessageContent), c = F((f) => f.setMessageSources), u = F((f) => f.addToolBlock), d = F((f) => f.updateToolBlockStatus), p = F((f) => f.updateToolBlockResult), m = F((f) => f.setConversationTitle), h = F((f) => f.addErrorBlock), g = Q(null), v = M(
    (f, w) => {
      if (!Ee.isMixed(f))
        return;
      const E = Ee.getMixedHandler(f);
      if (!E)
        return;
      const x = (b, I) => {
        vt(b);
      };
      try {
        const b = E(w, x);
        if (!b?.sources?.length)
          return;
        const I = G.getState().currentMessageId;
        if (I) {
          const C = b.sources.map((O) => ({
            content: O.content,
            sourceUrl: O.source_url,
            title: O.title,
            sourceType: O.source_type
          }));
          c(I, C);
        }
      } catch {
        vt(f);
      }
    },
    [c]
  );
  return { handleEvent: M(
    (f) => {
      if (f.type === H.CONVERSATION_TITLE && f.content)
        return m(f.content), { agentResultType: ln.CONVERSATION_TITLE, conversationTitle: f.content };
      const w = t(), E = e ?? Wr, x = yd({
        event: f,
        messageId: w.messageId,
        currentBlockId: w.currentThinkingBlockId,
        lastThinkingContent: w.lastThinkingContent,
        lastStatusLabel: w.lastStatusLabel,
        blockCount: w.blockCount,
        accumulatedContent: w.accumulatedContent,
        getStatusLabel: E
      });
      if (x.error)
        return x.thinkingAction && fn(x.thinkingAction, {
          addThinkingBlock: a,
          appendToThinkingBlock: i,
          completeThinkingBlock: s,
          completeAllThinkingBlocks: o
        }), G.getState().setError(x.error), { agentResultType: null };
      if (x.intermediateError && w.messageId) {
        x.thinkingAction && fn(x.thinkingAction, {
          addThinkingBlock: a,
          appendToThinkingBlock: i,
          completeThinkingBlock: s,
          completeAllThinkingBlocks: o
        }), G.getState().processEvent(f);
        const I = `error-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
        return h(w.messageId, I, x.intermediateError), n({
          currentThinkingBlockId: null,
          lastThinkingContent: "",
          lastStatusLabel: "",
          blockCount: w.blockCount + 1
        }), { agentResultType: null };
      }
      x.statusLabel && w.messageId && (r(w.messageId, x.statusLabel), n({ lastStatusLabel: x.statusLabel })), x.thinkingAction && (fn(x.thinkingAction, {
        addThinkingBlock: a,
        appendToThinkingBlock: i,
        completeThinkingBlock: s,
        completeAllThinkingBlocks: o
      }), x.thinkingAction.type === "ADD_BLOCK" && n({
        blockCount: w.blockCount + 1
      })), n({
        currentThinkingBlockId: x.newBlockId,
        lastThinkingContent: x.newThinkingContent,
        accumulatedContent: x.newAccumulatedContent
      }), x.finalContent?.trim() && w.messageId && l(w.messageId, x.finalContent);
      const b = G.getState().processEvent(f);
      if ((f.type === H.TOOL_INVOKE || f.type === H.EXTERNAL_TOOL_CALL) && w.messageId && f.toolName) {
        const I = vd(), C = (w.toolStepCount ?? 0) + 1;
        g.current = I;
        const O = f.toolArguments ? Gr(f.toolArguments) : void 0;
        u(w.messageId, I, f.toolName, C, O), n({ toolStepCount: C });
      }
      if (f.type === H.TOOL_RESULT && w.messageId && g.current) {
        const I = f.status === oe.ERROR ? "error" : "success";
        f.toolResult && p(w.messageId, g.current, f.toolResult), d(w.messageId, g.current, I), I === "error" && f.toolName && r(
          w.messageId,
          E(H.TOOL_RESULT, f.toolName, oe.ERROR)
        ), g.current = null;
      }
      return b?.type === "MIXED_TOOL_RESULT" && v(b.toolName, b.toolResult), {
        agentResultType: b?.type ?? null,
        toolName: b?.type === "TOOL_INVOKE" || b?.type === "MIXED_TOOL_RESULT" ? b.toolName : void 0,
        toolCallId: b?.type === "TOOL_INVOKE" ? b.toolCallId : void 0,
        toolArguments: b?.type === "TOOL_INVOKE" ? b.toolArguments : void 0,
        toolResult: b?.type === "MIXED_TOOL_RESULT" ? b.toolResult : void 0,
        conversationTitle: void 0
      };
    },
    [
      t,
      e,
      r,
      n,
      a,
      i,
      s,
      o,
      l,
      v,
      u,
      d,
      p,
      m,
      h
    ]
  ) };
}, wd = ({
  transport: e,
  onEvent: t,
  onInitializeMessage: n
}) => {
  const r = F((v) => v.addUserMessage), a = F((v) => v.addAssistantMessage), i = F((v) => v.clearMessages), s = G((v) => v.conversationId), o = G((v) => v.isBusy), l = G((v) => v.startConversation), c = G((v) => v.reset), u = M(
    async (v, k) => {
      if (!v.trim() || o)
        return;
      r(v);
      const f = s || crypto.randomUUID(), w = crypto.randomUUID();
      n(w), l(f, w), a(w), e.subscribe(f, t);
      try {
        await e.sendMessage({
          conversationId: f,
          messageId: w,
          content: v,
          externalTools: Ee.getToolsForBackend(),
          context: k
        });
      } catch (E) {
        const x = E instanceof Error ? E.message : "Failed to send message";
        G.getState().setError(x);
      }
    },
    [
      r,
      a,
      s,
      o,
      n,
      l,
      t,
      e
    ]
  ), d = M(() => {
    const v = G.getState().conversationId;
    v && e.haltConversation?.(v), c(), v && G.getState().setConversationId(v);
  }, [c, e]), p = M(() => {
    const v = G.getState().conversationId;
    v && (e.haltConversation?.(v), e.unsubscribe(v)), c(), v && G.getState().setConversationId(v);
  }, [c, e]), m = M(() => {
    const v = G.getState().conversationId;
    v && (e.haltConversation?.(v), e.unsubscribe(v)), c(), i();
  }, [c, i, e]), h = M(
    (v, k) => {
      e.updateConversation?.(v, k);
    },
    [e]
  ), g = M(
    async (v) => {
      const k = G.getState().conversationId;
      k && (e.haltConversation?.(k), e.unsubscribe(k)), await te.getState().selectConversation(v), e.subscribe(v, t);
    },
    [e, t]
  );
  return {
    sendMessage: u,
    stopGeneration: d,
    haltAndUnsubscribe: p,
    updateConversation: h,
    resetConversation: m,
    loadConversation: g
  };
}, xd = ({ transport: e }) => {
  const t = Q(e);
  se(() => {
    t.current = e;
  }, [e]), se(() => () => {
    const n = G.getState().conversationId;
    n && t.current.unsubscribe(n);
  }, []);
}, yt = (e = null) => ({
  messageId: e,
  currentThinkingBlockId: null,
  lastThinkingContent: "",
  lastStatusLabel: "",
  accumulatedContent: "",
  blockCount: 0,
  toolStepCount: 0
}), ag = ({
  transport: e,
  onToolInvoke: t,
  getStatusLabel: n = Wr
}) => {
  const r = G(Mc), a = G((_) => _.setCallbacks), i = F((_) => _.updateMessageStatus), s = F((_) => _.updateMessageStatusLabel), o = F((_) => _.setMessageError), l = Q(yt());
  se(() => {
    a({
      onMessageStatusUpdate: i,
      onStatusLabelClear: s,
      onMessageError: o
    });
  }, [a, i, s, o]);
  const c = M((_) => {
    l.current = { ...l.current, ..._ };
  }, []), u = M((_) => {
    l.current = yt(_);
  }, []), { executeToolAndSendResult: d } = bd({
    onToolInvoke: t,
    transport: e
  }), p = Q(null), m = M(() => l.current, []), h = kd({
    getStatusLabel: n,
    getMessageContext: m,
    onUpdateMessageContext: c
  });
  p.current = h.handleEvent;
  const g = Q(null), v = M(() => {
    g.current && (clearTimeout(g.current), g.current = null);
  }, []), k = M(() => {
    v(), g.current = setTimeout(() => {
      const _ = G.getState();
      if (_.isBusy || _.pendingToolInvoke) {
        const P = l.current.messageId;
        P && F.getState().completeAllThinkingBlocks(P), C.current(), l.current = yt(), G.getState().setError("The assistant stopped responding. Please try again.");
      }
    }, 18e4);
  }, [v]), f = M(
    (_) => {
      k();
      const P = p.current?.(_);
      P?.agentResultType === ln.TOOL_INVOKE && P.toolName && d(P.toolName, P.toolArguments ?? {}, P.toolCallId);
    },
    [d, k]
  ), {
    sendMessage: w,
    stopGeneration: E,
    haltAndUnsubscribe: x,
    resetConversation: b,
    loadConversation: I
  } = wd({
    transport: e,
    onEvent: f,
    onInitializeMessage: u
  }), C = Q(x);
  C.current = x;
  const O = M(
    (..._) => (k(), w(..._)),
    [w, k]
  );
  xd({ transport: e }), se(() => {
    te.getState().setTransport(e);
  }, [e]), se(() => () => {
    v();
  }, [v]);
  const q = M(() => {
    v(), b(), l.current = yt();
  }, [b, v]), A = M(() => {
    const _ = l.current.messageId;
    _ && F.getState().completeAllThinkingBlocks(_), v(), E(), l.current = yt();
  }, [E, v]), T = M(
    async (_) => {
      l.current = yt(), await I(_);
    },
    [I]
  );
  return {
    sendMessage: O,
    isBusy: r,
    stopGeneration: A,
    resetConversation: q,
    loadConversation: T
  };
}, rg = {
  BPMN_EDITING: "BPMN_EDITING",
  FORM_EDITING: "FORM_EDITING",
  DMN_EDITING: "DMN_EDITING",
  FEEL_EDITING: "FEEL_EDITING",
  BPMN_VIEWING: "BPMN_VIEWING",
  FORM_VIEWING: "FORM_VIEWING",
  DMN_VIEWING: "DMN_VIEWING",
  FILE_OPERATIONS: "FILE_OPERATIONS",
  FILE_CREATION: "FILE_CREATION",
  VALIDATION: "VALIDATION",
  INTEGRATION: "INTEGRATION"
}, ig = (e, t) => !e.requiredCapabilities || e.requiredCapabilities.length === 0 ? !0 : t?.length ? e.requiredCapabilities.every((n) => t.includes(n)) : !1, Ed = async (e, t) => {
  const n = e.query;
  if (typeof n != "string" || n.trim() === "")
    return t("camunda_docs_search", new Error("Query parameter is required and must be a non-empty string")), JSON.stringify({
      error: "Query parameter is required and must be a non-empty string",
      answer: "",
      sources: []
    });
  const r = n.trim(), a = _u();
  if (!a) {
    const i = "Kapa search is not available. Ensure CopilotChat is rendered with kapaAiIntegrationId.";
    return t("camunda_docs_search", new Error(i)), JSON.stringify({ error: i, answer: "", sources: [] });
  }
  try {
    const i = await a(r);
    return JSON.stringify({
      answer: i.answer,
      sources: i.sources
    });
  } catch (i) {
    const s = i instanceof Error ? i.message : "Unknown error occurred";
    return t("camunda_docs_search", i instanceof Error ? i : new Error(s)), JSON.stringify({
      error: s,
      answer: "",
      sources: []
    });
  }
}, sg = {
  name: "camunda_docs_search",
  displayName: "Searching docs",
  description: "Search the Camunda documentation and knowledge base for information. Use this tool when you need to find specific information, documentation, best practices, or answers about Camunda, BPMN, DMN, Forms, Connectors, or related topics.",
  parameters: {
    type: "object",
    properties: {
      query: {
        type: "string",
        description: "The search query to find information in the Camunda knowledge base"
      }
    },
    required: ["query"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "GENERAL",
  handler: Ed
}, cn = Object.prototype.toString, _d = Object.prototype.hasOwnProperty;
function Sn(e) {
  return e === void 0;
}
function Id(e) {
  return e !== void 0;
}
function Jr(e) {
  return e == null;
}
function Yr(e) {
  return cn.call(e) === "[object Array]";
}
function Cd(e) {
  return cn.call(e) === "[object Object]";
}
function Zr(e) {
  const t = cn.call(e);
  return t === "[object Function]" || t === "[object AsyncFunction]" || t === "[object GeneratorFunction]" || t === "[object AsyncGeneratorFunction]" || t === "[object Proxy]";
}
function pt(e) {
  return cn.call(e) === "[object String]";
}
function ei(e, t) {
  return !Jr(e) && _d.call(e, t);
}
function Za(e, t) {
  const n = $n(t);
  let r;
  return V(e, function(a, i) {
    if (n(a, i))
      return r = a, !1;
  }), r;
}
function Td(e, t) {
  const n = $n(t);
  let r = Yr(e) ? -1 : void 0;
  return V(e, function(a, i) {
    if (n(a, i))
      return r = i, !1;
  }), r;
}
function qn(e, t) {
  const n = $n(t);
  let r = [];
  return V(e, function(a, i) {
    n(a, i) && r.push(a);
  }), r;
}
function V(e, t) {
  let n, r;
  if (Sn(e))
    return;
  const a = Yr(e) ? Nd : Rd;
  for (let i in e)
    if (ei(e, i) && (n = e[i], r = t(n, a(i)), r === !1))
      return n;
}
function Sd(e, t) {
  let n = [];
  return V(e, function(r, a) {
    n.push(t(r, a));
  }), n;
}
function $n(e) {
  return Zr(e) ? e : (t) => t === e;
}
function Rd(e) {
  return e;
}
function Nd(e) {
  return Number(e);
}
function Et(e, t) {
  return e.bind(t);
}
function Y(e, ...t) {
  return Object.assign(e, ...t);
}
function Ad(e, t, n) {
  let r = e;
  return V(t, function(a, i) {
    if (typeof a != "number" && typeof a != "string")
      throw new Error("illegal key type: " + typeof a + ". Key should be of type number or string.");
    if (a === "constructor")
      throw new Error("illegal key: constructor");
    if (a === "__proto__")
      throw new Error("illegal key: __proto__");
    let s = t[i + 1], o = r[a];
    Id(s) && Jr(o) && (o = r[a] = isNaN(+s) ? {} : []), Sn(s) ? Sn(n) ? delete r[a] : r[a] = n : r = o;
  }), e;
}
function ti(e, t) {
  let n = {}, r = Object(e);
  return V(t, function(a) {
    a in r && (n[a] = e[a]);
  }), n;
}
function Un() {
}
Un.prototype.get = function(e) {
  return this.$model.properties.get(this, e);
};
Un.prototype.set = function(e, t) {
  this.$model.properties.set(this, e, t);
};
function ni(e, t) {
  this.model = e, this.properties = t;
}
ni.prototype.createType = function(e) {
  var t = this.model, n = this.properties, r = Object.create(Un.prototype);
  V(e.properties, function(s) {
    !s.isMany && s.default !== void 0 && (r[s.name] = s.default);
  }), n.defineModel(r, t), n.defineDescriptor(r, e);
  var a = e.ns.name;
  function i(s) {
    n.define(this, "$type", { value: a, enumerable: !0 }), n.define(this, "$attrs", { value: {} }), n.define(this, "$parent", { writable: !0 }), V(s, Et(function(o, l) {
      this.set(l, o);
    }, this));
  }
  return i.prototype = r, i.hasType = r.$instanceOf = this.model.hasType, n.defineModel(i, t), n.defineDescriptor(i, e), i;
};
var Md = {
  String: !0,
  Boolean: !0,
  Integer: !0,
  Real: !0,
  Element: !0
}, ai = {
  String: function(e) {
    return e;
  },
  Boolean: function(e) {
    return e === "true";
  },
  Integer: function(e) {
    return parseInt(e, 10);
  },
  Real: function(e) {
    return parseFloat(e);
  }
};
function Gn(e, t) {
  var n = ai[e];
  return n ? n(t) : t;
}
function Rn(e) {
  return !!Md[e];
}
function ri(e) {
  return !!ai[e];
}
function de(e, t) {
  var n = e.split(/:/), r, a;
  if (n.length === 1)
    r = e, a = t;
  else if (n.length === 2)
    r = n[1], a = n[0];
  else
    throw new Error("expected <prefix:localName> or <localName>, got " + e);
  return e = (a ? a + ":" : "") + r, {
    name: e,
    prefix: a,
    localName: r
  };
}
function _e(e) {
  this.ns = e, this.name = e.name, this.allTypes = [], this.allTypesByName = {}, this.properties = [], this.propertiesByName = {};
}
_e.prototype.build = function() {
  return ti(this, [
    "ns",
    "name",
    "allTypes",
    "allTypesByName",
    "properties",
    "propertiesByName",
    "bodyProperty",
    "idProperty"
  ]);
};
_e.prototype.addProperty = function(e, t, n) {
  typeof t == "boolean" && (n = t, t = void 0), this.addNamedProperty(e, n !== !1);
  var r = this.properties;
  t !== void 0 ? r.splice(t, 0, e) : r.push(e);
};
_e.prototype.replaceProperty = function(e, t, n) {
  var r = e.ns, a = this.properties, i = this.propertiesByName, s = e.name !== t.name;
  if (e.isId) {
    if (!t.isId)
      throw new Error(
        "property <" + t.ns.name + "> must be id property to refine <" + e.ns.name + ">"
      );
    this.setIdProperty(t, !1);
  }
  if (e.isBody) {
    if (!t.isBody)
      throw new Error(
        "property <" + t.ns.name + "> must be body property to refine <" + e.ns.name + ">"
      );
    this.setBodyProperty(t, !1);
  }
  var o = a.indexOf(e);
  if (o === -1)
    throw new Error("property <" + r.name + "> not found in property list");
  a.splice(o, 1), this.addProperty(t, n ? void 0 : o, s), i[r.name] = i[r.localName] = t;
};
_e.prototype.redefineProperty = function(e, t, n) {
  var r = e.ns.prefix, a = t.split("#"), i = de(a[0], r), s = de(a[1], i.prefix).name, o = this.propertiesByName[s];
  if (o)
    this.replaceProperty(o, e, n);
  else
    throw new Error("refined property <" + s + "> not found");
  delete e.redefines;
};
_e.prototype.addNamedProperty = function(e, t) {
  var n = e.ns, r = this.propertiesByName;
  t && (this.assertNotDefined(e, n.name), this.assertNotDefined(e, n.localName)), r[n.name] = r[n.localName] = e;
};
_e.prototype.removeNamedProperty = function(e) {
  var t = e.ns, n = this.propertiesByName;
  delete n[t.name], delete n[t.localName];
};
_e.prototype.setBodyProperty = function(e, t) {
  if (t && this.bodyProperty)
    throw new Error(
      "body property defined multiple times (<" + this.bodyProperty.ns.name + ">, <" + e.ns.name + ">)"
    );
  this.bodyProperty = e;
};
_e.prototype.setIdProperty = function(e, t) {
  if (t && this.idProperty)
    throw new Error(
      "id property defined multiple times (<" + this.idProperty.ns.name + ">, <" + e.ns.name + ">)"
    );
  this.idProperty = e;
};
_e.prototype.assertNotTrait = function(e) {
  if ((e.extends || []).length)
    throw new Error(
      `cannot create <${e.name}> extending <${e.extends}>`
    );
};
_e.prototype.assertNotDefined = function(e, t) {
  var n = e.name, r = this.propertiesByName[n];
  if (r)
    throw new Error(
      "property <" + n + "> already defined; override of <" + r.definedBy.ns.name + "#" + r.ns.name + "> by <" + e.definedBy.ns.name + "#" + e.ns.name + "> not allowed without redefines"
    );
};
_e.prototype.hasProperty = function(e) {
  return this.propertiesByName[e];
};
_e.prototype.addTrait = function(e, t) {
  t && this.assertNotTrait(e);
  var n = this.allTypesByName, r = this.allTypes, a = e.name;
  a in n || (V(e.properties, Et(function(i) {
    i = Y({}, i, {
      name: i.ns.localName,
      inherited: t
    }), Object.defineProperty(i, "definedBy", {
      value: e
    });
    var s = i.replaces, o = i.redefines;
    s || o ? this.redefineProperty(i, s || o, s) : (i.isBody && this.setBodyProperty(i), i.isId && this.setIdProperty(i), this.addProperty(i));
  }, this)), r.push(e), n[a] = e);
};
function st(e, t) {
  this.packageMap = {}, this.typeMap = {}, this.packages = [], this.properties = t, V(e, Et(this.registerPackage, this));
}
st.prototype.getPackage = function(e) {
  return this.packageMap[e];
};
st.prototype.getPackages = function() {
  return this.packages;
};
st.prototype.registerPackage = function(e) {
  e = Y({}, e);
  var t = this.packageMap;
  er(t, e, "prefix"), er(t, e, "uri"), V(e.types, Et(function(n) {
    this.registerType(n, e);
  }, this)), t[e.uri] = t[e.prefix] = e, this.packages.push(e);
};
st.prototype.registerType = function(e, t) {
  e = Y({}, e, {
    superClass: (e.superClass || []).slice(),
    extends: (e.extends || []).slice(),
    properties: (e.properties || []).slice(),
    meta: Y(e.meta || {})
  });
  var n = de(e.name, t.prefix), r = n.name, a = {};
  V(e.properties, Et(function(i) {
    var s = de(i.name, n.prefix), o = s.name;
    Rn(i.type) || (i.type = de(i.type, s.prefix).name), Y(i, {
      ns: s,
      name: o
    }), a[o] = i;
  }, this)), Y(e, {
    ns: n,
    name: r,
    propertiesByName: a
  }), V(e.extends, Et(function(i) {
    var s = de(i, n.prefix), o = this.typeMap[s.name];
    o.traits = o.traits || [], o.traits.push(r);
  }, this)), this.definePackage(e, t), this.typeMap[r] = e;
};
st.prototype.mapTypes = function(e, t, n) {
  var r = Rn(e.name) ? { name: e.name } : this.typeMap[e.name], a = this;
  function i(l, c) {
    var u = de(l, Rn(l) ? "" : e.prefix);
    a.mapTypes(u, t, c);
  }
  function s(l) {
    return i(l, !0);
  }
  function o(l) {
    return i(l, !1);
  }
  if (!r)
    throw new Error("unknown type <" + e.name + ">");
  V(r.superClass, n ? s : o), t(r, !n), V(r.traits, s);
};
st.prototype.getEffectiveDescriptor = function(e) {
  var t = de(e), n = new _e(t);
  this.mapTypes(t, function(a, i) {
    n.addTrait(a, i);
  });
  var r = n.build();
  return this.definePackage(r, r.allTypes[r.allTypes.length - 1].$pkg), r;
};
st.prototype.definePackage = function(e, t) {
  this.properties.define(e, "$pkg", { value: t });
};
function er(e, t, n) {
  var r = t[n];
  if (r in e)
    throw new Error("package with " + n + " <" + r + "> already defined");
}
function mt(e) {
  this.model = e;
}
mt.prototype.set = function(e, t, n) {
  if (!pt(t) || !t.length)
    throw new TypeError("property name must be a non-empty string");
  var r = this.getProperty(e, t), a = r && r.name;
  Od(n) ? r ? delete e[a] : delete e.$attrs[Nn(t)] : r ? a in e ? e[a] = n : ii(e, r, n) : e.$attrs[Nn(t)] = n;
};
mt.prototype.get = function(e, t) {
  var n = this.getProperty(e, t);
  if (!n)
    return e.$attrs[Nn(t)];
  var r = n.name;
  return !e[r] && n.isMany && ii(e, n, []), e[r];
};
mt.prototype.define = function(e, t, n) {
  if (!n.writable) {
    var r = n.value;
    n = Y({}, n, {
      get: function() {
        return r;
      }
    }), delete n.value;
  }
  Object.defineProperty(e, t, n);
};
mt.prototype.defineDescriptor = function(e, t) {
  this.define(e, "$descriptor", { value: t });
};
mt.prototype.defineModel = function(e, t) {
  this.define(e, "$model", { value: t });
};
mt.prototype.getProperty = function(e, t) {
  var n = this.model, r = n.getPropertyDescriptor(e, t);
  if (r)
    return r;
  if (t.includes(":"))
    return null;
  const a = n.config.strict;
  if (typeof a < "u") {
    const i = new TypeError(`unknown property <${t}> on <${e.$type}>`);
    if (a)
      throw i;
    typeof console < "u" && console.warn(i);
  }
  return null;
};
function Od(e) {
  return typeof e > "u";
}
function ii(e, t, n) {
  Object.defineProperty(e, t.name, {
    enumerable: !t.isReference,
    writable: !0,
    value: n,
    configurable: !0
  });
}
function Nn(e) {
  return e.replace(/^:/, "");
}
function Me(e, t = {}) {
  this.properties = new mt(this), this.factory = new ni(this, this.properties), this.registry = new st(e, this.properties), this.typeCache = {}, this.config = t;
}
Me.prototype.create = function(e, t) {
  var n = this.getType(e);
  if (!n)
    throw new Error("unknown type <" + e + ">");
  return new n(t);
};
Me.prototype.getType = function(e) {
  var t = this.typeCache, n = pt(e) ? e : e.ns.name, r = t[n];
  return r || (e = this.registry.getEffectiveDescriptor(n), r = t[n] = this.factory.createType(e)), r;
};
Me.prototype.createAny = function(e, t, n) {
  var r = de(e), a = {
    $type: e,
    $instanceOf: function(s) {
      return s === this.$type;
    },
    get: function(s) {
      return this[s];
    },
    set: function(s, o) {
      Ad(this, [s], o);
    }
  }, i = {
    name: e,
    isGeneric: !0,
    ns: {
      prefix: r.prefix,
      localName: r.localName,
      uri: t
    }
  };
  return this.properties.defineDescriptor(a, i), this.properties.defineModel(a, this), this.properties.define(a, "get", { enumerable: !1, writable: !0 }), this.properties.define(a, "set", { enumerable: !1, writable: !0 }), this.properties.define(a, "$parent", { enumerable: !1, writable: !0 }), this.properties.define(a, "$instanceOf", { enumerable: !1, writable: !0 }), V(n, function(s, o) {
    Cd(s) && s.value !== void 0 ? a[s.name] = s.value : a[o] = s;
  }), a;
};
Me.prototype.getPackage = function(e) {
  return this.registry.getPackage(e);
};
Me.prototype.getPackages = function() {
  return this.registry.getPackages();
};
Me.prototype.getElementDescriptor = function(e) {
  return e.$descriptor;
};
Me.prototype.hasType = function(e, t) {
  t === void 0 && (t = e, e = this);
  var n = e.$model.getElementDescriptor(e);
  return t in n.allTypesByName;
};
Me.prototype.getPropertyDescriptor = function(e, t) {
  return this.getElementDescriptor(e).propertiesByName[t];
};
Me.prototype.getTypeDescriptor = function(e) {
  return this.registry.typeMap[e];
};
var tr = String.fromCharCode, Pd = Object.prototype.hasOwnProperty, Dd = /&#(\d+);|&#x([0-9a-f]+);|&(\w+);/ig, Pt = {
  amp: "&",
  apos: "'",
  gt: ">",
  lt: "<",
  quot: '"'
};
Object.keys(Pt).forEach(function(e) {
  Pt[e.toUpperCase()] = Pt[e];
});
function jd(e, t, n, r) {
  return r ? Pd.call(Pt, r) ? Pt[r] : "&" + r + ";" : tr(t || parseInt(n, 16));
}
function lt(e) {
  return e.length > 3 && e.indexOf("&") !== -1 ? e.replace(Dd, jd) : e;
}
var nr = "non-whitespace outside of root node";
function bt(e) {
  return new Error(e);
}
function ar(e) {
  return "missing namespace for prefix <" + e + ">";
}
function Wt(e) {
  return {
    get: e,
    enumerable: !0
  };
}
function Bd(e) {
  var t = {}, n;
  for (n in e)
    t[n] = e[n];
  return t;
}
function An(e) {
  return e + "$uri";
}
function zd(e) {
  var t = {}, n, r;
  for (n in e)
    r = e[n], t[r] = r, t[An(r)] = n;
  return t;
}
function rr() {
  return { line: 0, column: 0 };
}
function Ld(e) {
  throw e;
}
function si(e) {
  if (!this)
    return new si(e);
  var t = e && e.proxy, n, r, a, i, s = Ld, o, l, c, u, d = rr, p = !1, m = !1, h = null, g = !1, v;
  function k(E) {
    E instanceof Error || (E = bt(E)), h = E, s(E, d);
  }
  function f(E) {
    o && (E instanceof Error || (E = bt(E)), o(E, d));
  }
  this.on = function(E, x) {
    if (typeof x != "function")
      throw bt("required args <name, cb>");
    switch (E) {
      case "openTag":
        r = x;
        break;
      case "text":
        n = x;
        break;
      case "closeTag":
        a = x;
        break;
      case "error":
        s = x;
        break;
      case "warn":
        o = x;
        break;
      case "cdata":
        i = x;
        break;
      case "attention":
        u = x;
        break;
      // <!XXXXX zzzz="eeee">
      case "question":
        c = x;
        break;
      // <? ....  ?>
      case "comment":
        l = x;
        break;
      default:
        throw bt("unsupported event: " + E);
    }
    return this;
  }, this.ns = function(E) {
    if (typeof E > "u" && (E = {}), typeof E != "object")
      throw bt("required args <nsMap={}>");
    var x = {}, b;
    for (b in E)
      x[b] = E[b];
    return m = !0, v = x, this;
  }, this.parse = function(E) {
    if (typeof E != "string")
      throw bt("required args <xml=string>");
    return h = null, w(E), d = rr, g = !1, h;
  }, this.stop = function() {
    g = !0;
  };
  function w(E) {
    var x = m ? [] : null, b = m ? zd(v) : null, I, C = [], O = 0, q = !1, A = !1, T = 0, _ = 0, P, Oe, $, D, ze, Pe, ne, B, Le, et = "", tt = 0, Ie;
    function S() {
      if (Ie !== null)
        return Ie;
      var Ce, Te, fe, Fe = m && b.xmlns, Se = m && p ? [] : null, K = tt, ae = et, qe = ae.length, Nt, ge, Re, ye, U, $e = {}, At = {}, he, R, z;
      e:
        for (; K < qe; K++)
          if (he = !1, R = ae.charCodeAt(K), !(R === 32 || R < 14 && R > 8)) {
            for ((R < 65 || R > 122 || R > 90 && R < 97) && R !== 95 && R !== 58 && (f("illegal first char attribute name"), he = !0), z = K + 1; z < qe; z++)
              if (R = ae.charCodeAt(z), !(R > 96 && R < 123 || R > 64 && R < 91 || R > 47 && R < 59 || R === 46 || // '.'
              R === 45 || // '-'
              R === 95)) {
                if (R === 32 || R < 14 && R > 8) {
                  f("missing attribute value"), K = z;
                  continue e;
                }
                if (R === 61)
                  break;
                f("illegal attribute name char"), he = !0;
              }
            if (U = ae.substring(K, z), U === "xmlns:xmlns" && (f("illegal declaration of xmlns"), he = !0), R = ae.charCodeAt(z + 1), R === 34)
              z = ae.indexOf('"', K = z + 2), z === -1 && (z = ae.indexOf("'", K), z !== -1 && (f("attribute value quote missmatch"), he = !0));
            else if (R === 39)
              z = ae.indexOf("'", K = z + 2), z === -1 && (z = ae.indexOf('"', K), z !== -1 && (f("attribute value quote missmatch"), he = !0));
            else
              for (f("missing attribute value quotes"), he = !0, z = z + 1; z < qe && (R = ae.charCodeAt(z + 1), !(R === 32 || R < 14 && R > 8)); z++)
                ;
            for (z === -1 && (f("missing closing quotes"), z = qe, he = !0), he || (Re = ae.substring(K, z)), K = z; z + 1 < qe && (R = ae.charCodeAt(z + 1), !(R === 32 || R < 14 && R > 8)); z++)
              K === z && (f("illegal character after attribute end"), he = !0);
            if (K = z + 1, he)
              continue e;
            if (U in At) {
              f("attribute <" + U + "> already defined");
              continue;
            }
            if (At[U] = !0, !m) {
              $e[U] = Re;
              continue;
            }
            if (p) {
              if (ge = U === "xmlns" ? "xmlns" : U.charCodeAt(0) === 120 && U.substr(0, 6) === "xmlns:" ? U.substr(6) : null, ge !== null) {
                if (Ce = lt(Re), Te = An(ge), ye = v[Ce], !ye) {
                  if (ge === "xmlns" || Te in b && b[Te] !== Ce)
                    do
                      ye = "ns" + O++;
                    while (typeof b[ye] < "u");
                  else
                    ye = ge;
                  v[Ce] = ye;
                }
                b[ge] !== ye && (Nt || (b = Bd(b), Nt = !0), b[ge] = ye, ge === "xmlns" && (b[An(ye)] = Ce, Fe = ye), b[Te] = Ce), $e[U] = Re;
                continue;
              }
              Se.push(U, Re);
              continue;
            }
            if (R = U.indexOf(":"), R === -1) {
              $e[U] = Re;
              continue;
            }
            if (!(fe = b[U.substring(0, R)])) {
              f(ar(U.substring(0, R)));
              continue;
            }
            U = Fe === fe ? U.substr(R + 1) : fe + U.substr(R), $e[U] = Re;
          }
      if (p)
        for (K = 0, qe = Se.length; K < qe; K++) {
          if (U = Se[K++], Re = Se[K], R = U.indexOf(":"), R !== -1) {
            if (!(fe = b[U.substring(0, R)])) {
              f(ar(U.substring(0, R)));
              continue;
            }
            U = Fe === fe ? U.substr(R + 1) : fe + U.substr(R);
          }
          $e[U] = Re;
        }
      return Ie = $e;
    }
    function Rt() {
      for (var Ce = /(\r\n|\r|\n)/g, Te = 0, fe = 0, Fe = 0, Se = _, K, ae; T >= Fe && (K = Ce.exec(E), !(!K || (Se = K[0].length + K.index, Se > T))); )
        Te += 1, Fe = Se;
      return T == -1 ? (fe = Se, ae = E.substring(_)) : _ === 0 ? ae = E.substring(_, T) : (fe = T - Fe, ae = _ == -1 ? E.substring(T) : E.substring(T, _ + 1)), {
        data: ae,
        line: Te,
        column: fe
      };
    }
    for (d = Rt, t && (Le = Object.create({}, {
      name: Wt(function() {
        return ne;
      }),
      originalName: Wt(function() {
        return B;
      }),
      attrs: Wt(S),
      ns: Wt(function() {
        return b;
      })
    })); _ !== -1; ) {
      if (E.charCodeAt(_) === 60 ? T = _ : T = E.indexOf("<", _), T === -1) {
        if (C.length)
          return k("unexpected end of file");
        if (_ === 0)
          return k("missing start tag");
        _ < E.length && E.substring(_).trim() && f(nr);
        return;
      }
      if (_ !== T) {
        if (C.length) {
          if (n && (n(E.substring(_, T), lt, d), g))
            return;
        } else if (E.substring(_, T).trim() && (f(nr), g))
          return;
      }
      if (D = E.charCodeAt(T + 1), D === 33) {
        if ($ = E.charCodeAt(T + 2), $ === 91 && E.substr(T + 3, 6) === "CDATA[") {
          if (_ = E.indexOf("]]>", T), _ === -1)
            return k("unclosed cdata");
          if (i && (i(E.substring(T + 9, _), d), g))
            return;
          _ += 3;
          continue;
        }
        if ($ === 45 && E.charCodeAt(T + 3) === 45) {
          if (_ = E.indexOf("-->", T), _ === -1)
            return k("unclosed comment");
          if (l && (l(E.substring(T + 4, _), lt, d), g))
            return;
          _ += 3;
          continue;
        }
      }
      if (D === 63) {
        if (_ = E.indexOf("?>", T), _ === -1)
          return k("unclosed question");
        if (c && (c(E.substring(T, _ + 2), d), g))
          return;
        _ += 2;
        continue;
      }
      for (P = T + 1; ; P++) {
        if (ze = E.charCodeAt(P), isNaN(ze))
          return _ = -1, k("unclosed tag");
        if (ze === 34)
          $ = E.indexOf('"', P + 1), P = $ !== -1 ? $ : P;
        else if (ze === 39)
          $ = E.indexOf("'", P + 1), P = $ !== -1 ? $ : P;
        else if (ze === 62) {
          _ = P;
          break;
        }
      }
      if (D === 33) {
        if (u && (u(E.substring(T, _ + 1), lt, d), g))
          return;
        _ += 1;
        continue;
      }
      if (Ie = {}, D === 47) {
        if (q = !1, A = !0, !C.length)
          return k("missing open tag");
        if (P = ne = C.pop(), $ = T + 2 + P.length, E.substring(T + 2, $) !== P)
          return k("closing tag mismatch");
        for (; $ < _; $++)
          if (D = E.charCodeAt($), !(D === 32 || D > 8 && D < 14))
            return k("close tag");
      } else {
        if (E.charCodeAt(_ - 1) === 47 ? (P = ne = E.substring(T + 1, _ - 1), q = !0, A = !0) : (P = ne = E.substring(T + 1, _), q = !0, A = !1), !(D > 96 && D < 123 || D > 64 && D < 91 || D === 95 || D === 58))
          return k("illegal first char nodeName");
        for ($ = 1, Oe = P.length; $ < Oe; $++)
          if (D = P.charCodeAt($), !(D > 96 && D < 123 || D > 64 && D < 91 || D > 47 && D < 59 || D === 45 || D === 95 || D == 46)) {
            if (D === 32 || D < 14 && D > 8) {
              ne = P.substring(0, $), Ie = null;
              break;
            }
            return k("invalid nodeName");
          }
        A || C.push(ne);
      }
      if (m) {
        if (I = b, q && (A || x.push(I), Ie === null && (p = P.indexOf("xmlns", $) !== -1) && (tt = $, et = P, S(), p = !1)), B = ne, D = ne.indexOf(":"), D !== -1) {
          if (Pe = b[ne.substring(0, D)], !Pe)
            return k("missing namespace on <" + B + ">");
          ne = ne.substr(D + 1);
        } else
          Pe = b.xmlns;
        Pe && (ne = Pe + ":" + ne);
      }
      if (q && (tt = $, et = P, r && (t ? r(Le, lt, A, d) : r(ne, S, lt, A, d), g)))
        return;
      if (A) {
        if (a && (a(t ? Le : ne, lt, q, d), g))
          return;
        m && (q ? b = I : b = x.pop());
      }
      _ += 1;
    }
  }
}
function oi(e) {
  return e.xml && e.xml.tagAlias === "lowerCase";
}
var Mn = {
  xsi: "http://www.w3.org/2001/XMLSchema-instance",
  xml: "http://www.w3.org/XML/1998/namespace"
}, li = "property";
function ci(e) {
  return e.xml && e.xml.serialize;
}
function Fd(e) {
  const t = ci(e);
  return t !== li && (t || null);
}
function qd(e) {
  return e.charAt(0).toUpperCase() + e.slice(1);
}
function ui(e, t) {
  return oi(t) ? e.prefix + ":" + qd(e.localName) : e.name;
}
function $d(e, t) {
  var n = e.name, r = e.localName, a = t && t.xml && t.xml.typePrefix;
  return a && r.indexOf(a) === 0 ? e.prefix + ":" + r.slice(a.length) : n;
}
function Ud(e, t, n) {
  const r = de(e, t.xmlns), a = `${t[r.prefix] || r.prefix}:${r.localName}`, i = de(a);
  var s = n.getPackage(i.prefix);
  return $d(i, s);
}
function it(e) {
  return new Error(e);
}
function Xe(e) {
  return e.$descriptor;
}
function Gd(e) {
  Y(this, e), this.elementsById = {}, this.references = [], this.warnings = [], this.addReference = function(t) {
    this.references.push(t);
  }, this.addElement = function(t) {
    if (!t)
      throw it("expected element");
    var n = this.elementsById, r = Xe(t), a = r.idProperty, i;
    if (a && (i = t.get(a.name), i)) {
      if (!/^([a-z][\w-.]*:)?[a-z_][\w-.]*$/i.test(i))
        throw new Error("illegal ID <" + i + ">");
      if (n[i])
        throw it("duplicate ID <" + i + ">");
      n[i] = t;
    }
  }, this.addWarning = function(t) {
    this.warnings.push(t);
  };
}
function zt() {
}
zt.prototype.handleEnd = function() {
};
zt.prototype.handleText = function() {
};
zt.prototype.handleNode = function() {
};
function Hn() {
}
Hn.prototype = Object.create(zt.prototype);
Hn.prototype.handleNode = function() {
  return this;
};
function Tt() {
}
Tt.prototype = Object.create(zt.prototype);
Tt.prototype.handleText = function(e) {
  this.body = (this.body || "") + e;
};
function Lt(e, t) {
  this.property = e, this.context = t;
}
Lt.prototype = Object.create(Tt.prototype);
Lt.prototype.handleNode = function(e) {
  if (this.element)
    throw it("expected no sub nodes");
  return this.element = this.createReference(e), this;
};
Lt.prototype.handleEnd = function() {
  this.element.id = this.body;
};
Lt.prototype.createReference = function(e) {
  return {
    property: this.property.ns.name,
    id: ""
  };
};
function Vn(e, t) {
  this.element = t, this.propertyDesc = e;
}
Vn.prototype = Object.create(Tt.prototype);
Vn.prototype.handleEnd = function() {
  var e = this.body || "", t = this.element, n = this.propertyDesc;
  e = Gn(n.type, e), n.isMany ? t.get(n.name).push(e) : t.set(n.name, e);
};
function un() {
}
un.prototype = Object.create(Tt.prototype);
un.prototype.handleNode = function(e) {
  var t = this, n = this.element;
  return n ? t = this.handleChild(e) : (n = this.element = this.createElement(e), this.context.addElement(n)), t;
};
function me(e, t, n) {
  this.model = e, this.type = e.getType(t), this.context = n;
}
me.prototype = Object.create(un.prototype);
me.prototype.addReference = function(e) {
  this.context.addReference(e);
};
me.prototype.handleText = function(e) {
  var t = this.element, n = Xe(t), r = n.bodyProperty;
  if (!r)
    throw it("unexpected body text <" + e + ">");
  Tt.prototype.handleText.call(this, e);
};
me.prototype.handleEnd = function() {
  var e = this.body, t = this.element, n = Xe(t), r = n.bodyProperty;
  r && e !== void 0 && (e = Gn(r.type, e), t.set(r.name, e));
};
me.prototype.createElement = function(e) {
  var t = e.attributes, n = this.type, r = Xe(n), a = this.context, i = new n({}), s = this.model, o;
  return V(t, function(l, c) {
    var u = r.propertiesByName[c], d;
    u && u.isReference ? u.isMany ? (d = l.split(" "), V(d, function(p) {
      a.addReference({
        element: i,
        property: u.ns.name,
        id: p
      });
    })) : a.addReference({
      element: i,
      property: u.ns.name,
      id: l
    }) : (u ? l = Gn(u.type, l) : c === "xmlns" ? c = ":" + c : (o = de(c, r.ns.prefix), s.getPackage(o.prefix) && a.addWarning({
      message: "unknown attribute <" + c + ">",
      element: i,
      property: c,
      value: l
    })), i.set(c, l));
  }), i;
};
me.prototype.getPropertyForNode = function(e) {
  var t = e.name, n = de(t), r = this.type, a = this.model, i = Xe(r), s = n.name, o = i.propertiesByName[s];
  if (o && !o.isAttr) {
    const c = Fd(o);
    if (c) {
      const u = e.attributes[c];
      if (u) {
        const d = Ud(u, e.ns, a), p = a.getType(d);
        return Y({}, o, {
          effectiveType: Xe(p).name
        });
      }
    }
    return o;
  }
  var l = a.getPackage(n.prefix);
  if (l) {
    const c = ui(n, l), u = a.getType(c);
    if (o = Za(i.properties, function(d) {
      return !d.isVirtual && !d.isReference && !d.isAttribute && u.hasType(d.type);
    }), o)
      return Y({}, o, {
        effectiveType: Xe(u).name
      });
  } else if (o = Za(i.properties, function(c) {
    return !c.isReference && !c.isAttribute && c.type === "Element";
  }), o)
    return o;
  throw it("unrecognized element <" + n.name + ">");
};
me.prototype.toString = function() {
  return "ElementDescriptor[" + Xe(this.type).name + "]";
};
me.prototype.valueHandler = function(e, t) {
  return new Vn(e, t);
};
me.prototype.referenceHandler = function(e) {
  return new Lt(e, this.context);
};
me.prototype.handler = function(e) {
  return e === "Element" ? new _t(this.model, e, this.context) : new me(this.model, e, this.context);
};
me.prototype.handleChild = function(e) {
  var t, n, r, a;
  if (t = this.getPropertyForNode(e), r = this.element, n = t.effectiveType || t.type, ri(n))
    return this.valueHandler(t, r);
  t.isReference ? a = this.referenceHandler(t).handleNode(e) : a = this.handler(n).handleNode(e);
  var i = a.element;
  return i !== void 0 && (t.isMany ? r.get(t.name).push(i) : r.set(t.name, i), t.isReference ? (Y(i, {
    element: r
  }), this.context.addReference(i)) : i.$parent = r), a;
};
function Kn(e, t, n) {
  me.call(this, e, t, n);
}
Kn.prototype = Object.create(me.prototype);
Kn.prototype.createElement = function(e) {
  var t = e.name, n = de(t), r = this.model, a = this.type, i = r.getPackage(n.prefix), s = i && ui(n, i) || t;
  if (!a.hasType(s))
    throw it("unexpected element <" + e.originalName + ">");
  return me.prototype.createElement.call(this, e);
};
function _t(e, t, n) {
  this.model = e, this.context = n;
}
_t.prototype = Object.create(un.prototype);
_t.prototype.createElement = function(e) {
  var t = e.name, n = de(t), r = n.prefix, a = e.ns[r + "$uri"], i = e.attributes;
  return this.model.createAny(t, a, i);
};
_t.prototype.handleChild = function(e) {
  var t = new _t(this.model, "Element", this.context).handleNode(e), n = this.element, r = t.element, a;
  return r !== void 0 && (a = n.$children = n.$children || [], a.push(r), r.$parent = n), t;
};
_t.prototype.handleEnd = function() {
  this.body && (this.element.$body = this.body);
};
function Wn(e) {
  e instanceof Me && (e = {
    model: e
  }), Y(this, { lax: !1 }, e);
}
Wn.prototype.fromXML = function(e, t, n) {
  var r = t.rootHandler;
  t instanceof me ? (r = t, t = {}) : typeof t == "string" ? (r = this.handler(t), t = {}) : typeof r == "string" && (r = this.handler(r));
  var a = this.model, i = this.lax, s = new Gd(Y({}, t, { rootHandler: r })), o = new si({ proxy: !0 }), l = Hd();
  r.context = s, l.push(r);
  function c(x, b, I) {
    var C = b(), O = C.line, q = C.column, A = C.data;
    A.charAt(0) === "<" && A.indexOf(" ") !== -1 && (A = A.slice(0, A.indexOf(" ")) + ">");
    var T = "unparsable content " + (A ? A + " " : "") + `detected
	line: ` + O + `
	column: ` + q + `
	nested error: ` + x.message;
    if (I)
      return s.addWarning({
        message: T,
        error: x
      }), !0;
    throw it(T);
  }
  function u(x, b) {
    return c(x, b, !0);
  }
  function d() {
    var x = s.elementsById, b = s.references, I, C;
    for (I = 0; C = b[I]; I++) {
      var O = C.element, q = x[C.id], A = Xe(O).propertiesByName[C.property];
      if (q || s.addWarning({
        message: "unresolved reference <" + C.id + ">",
        element: C.element,
        property: C.property,
        value: C.id
      }), A.isMany) {
        var T = O.get(A.name), _ = T.indexOf(C);
        _ === -1 && (_ = T.length), q ? T[_] = q : T.splice(_, 1);
      } else
        O.set(A.name, q);
    }
  }
  function p() {
    l.pop().handleEnd();
  }
  var m = /^<\?xml /i, h = / encoding="([^"]+)"/i, g = /^utf-8$/i;
  function v(x) {
    if (m.test(x)) {
      var b = h.exec(x), I = b && b[1];
      !I || g.test(I) || s.addWarning({
        message: "unsupported document encoding <" + I + ">, falling back to UTF-8"
      });
    }
  }
  function k(x, b) {
    var I = l.peek();
    try {
      l.push(I.handleNode(x));
    } catch (C) {
      c(C, b, i) && l.push(new Hn());
    }
  }
  function f(x, b) {
    try {
      l.peek().handleText(x);
    } catch (I) {
      u(I, b);
    }
  }
  function w(x, b) {
    x.trim() && f(x, b);
  }
  var E = a.getPackages().reduce(function(x, b) {
    return x[b.uri] = b.prefix, x;
  }, Object.entries(Mn).reduce(function(x, [b, I]) {
    return x[I] = b, x;
  }, a.config && a.config.nsMap || {}));
  return o.ns(E).on("openTag", function(x, b, I, C) {
    var O = x.attrs || {}, q = Object.keys(O).reduce(function(T, _) {
      var P = b(O[_]);
      return T[_] = P, T;
    }, {}), A = {
      name: x.name,
      originalName: x.originalName,
      attributes: q,
      ns: x.ns
    };
    k(A, C);
  }).on("question", v).on("closeTag", p).on("cdata", f).on("text", function(x, b, I) {
    w(b(x), I);
  }).on("error", c).on("warn", u), new Promise(function(x, b) {
    var I;
    try {
      o.parse(e), d();
    } catch (T) {
      I = T;
    }
    var C = r.element;
    !I && !C && (I = it("failed to parse document as <" + r.type.$descriptor.name + ">"));
    var O = s.warnings, q = s.references, A = s.elementsById;
    return I ? (I.warnings = O, b(I)) : x({
      rootElement: C,
      elementsById: A,
      references: q,
      warnings: O
    });
  });
};
Wn.prototype.handler = function(e) {
  return new Kn(this.model, e);
};
function Hd() {
  var e = [];
  return Object.defineProperty(e, "peek", {
    value: function() {
      return this[this.length - 1];
    }
  }), e;
}
var Vd = `<?xml version="1.0" encoding="UTF-8"?>
`, Kd = /<|>|'|"|&|\n\r|\n/g, di = /<|>|&/g;
function Ve(e) {
  this.prefixMap = {}, this.uriMap = {}, this.used = {}, this.wellknown = [], this.custom = [], this.parent = e, this.defaultPrefixMap = e && e.defaultPrefixMap || {};
}
Ve.prototype.mapDefaultPrefixes = function(e) {
  this.defaultPrefixMap = e;
};
Ve.prototype.defaultUriByPrefix = function(e) {
  return this.defaultPrefixMap[e];
};
Ve.prototype.byUri = function(e) {
  return this.uriMap[e] || this.parent && this.parent.byUri(e);
};
Ve.prototype.add = function(e, t) {
  this.uriMap[e.uri] = e, t ? this.wellknown.push(e) : this.custom.push(e), this.mapPrefix(e.prefix, e.uri);
};
Ve.prototype.uriByPrefix = function(e) {
  return this.prefixMap[e || "xmlns"] || this.parent && this.parent.uriByPrefix(e);
};
Ve.prototype.mapPrefix = function(e, t) {
  this.prefixMap[e || "xmlns"] = t;
};
Ve.prototype.getNSKey = function(e) {
  return e.prefix !== void 0 ? e.uri + "|" + e.prefix : e.uri;
};
Ve.prototype.logUsed = function(e) {
  var t = e.uri, n = this.getNSKey(e);
  this.used[n] = this.byUri(t), this.parent && this.parent.logUsed(e);
};
Ve.prototype.getUsed = function(e) {
  var t = [].concat(this.wellknown, this.custom);
  return t.filter((n) => {
    var r = this.getNSKey(n);
    return this.used[r];
  });
};
function Wd(e) {
  return e.charAt(0).toLowerCase() + e.slice(1);
}
function Xd(e, t) {
  return oi(t) ? Wd(e) : e;
}
function pi(e, t) {
  e.super_ = t, e.prototype = Object.create(t.prototype, {
    constructor: {
      value: e,
      enumerable: !1,
      writable: !0,
      configurable: !0
    }
  });
}
function mi(e) {
  return pt(e) ? e : (e.prefix ? e.prefix + ":" : "") + e.localName;
}
function Qd(e) {
  return e.getUsed().filter(function(t) {
    return t.prefix !== "xml";
  }).map(function(t) {
    var n = "xmlns" + (t.prefix ? ":" + t.prefix : "");
    return { name: n, value: t.uri };
  });
}
function Jd(e, t) {
  return t.isGeneric ? Y({ localName: t.ns.localName }, e) : Y({ localName: Xd(t.ns.localName, t.$pkg) }, e);
}
function Yd(e, t) {
  return Y({ localName: t.ns.localName }, e);
}
function Zd(e) {
  var t = e.$descriptor;
  return qn(t.properties, function(n) {
    var r = n.name;
    if (n.isVirtual || !ei(e, r))
      return !1;
    var a = e[r];
    return a === n.default || a === null ? !1 : n.isMany ? a.length : !0;
  });
}
var ep = {
  "\n": "#10",
  "\n\r": "#10",
  '"': "#34",
  "'": "#39",
  "<": "#60",
  ">": "#62",
  "&": "#38"
}, tp = {
  "<": "lt",
  ">": "gt",
  "&": "amp"
};
function hi(e, t, n) {
  return e = pt(e) ? e : "" + e, e.replace(t, function(r) {
    return "&" + n[r] + ";";
  });
}
function np(e) {
  return hi(e, Kd, ep);
}
function ap(e) {
  return hi(e, di, tp);
}
function rp(e) {
  return qn(e, function(t) {
    return t.isAttr;
  });
}
function ip(e) {
  return qn(e, function(t) {
    return !t.isAttr;
  });
}
function Xn(e) {
  this.tagName = e;
}
Xn.prototype.build = function(e) {
  return this.element = e, this;
};
Xn.prototype.serializeTo = function(e) {
  e.appendIndent().append("<" + this.tagName + ">" + this.element.id + "</" + this.tagName + ">").appendNewLine();
};
function dt() {
}
dt.prototype.serializeValue = dt.prototype.serializeTo = function(e) {
  e.append(
    this.escape ? ap(this.value) : this.value
  );
};
dt.prototype.build = function(e, t) {
  return this.value = t, e.type === "String" && t.search(di) !== -1 && (this.escape = !0), this;
};
function Qn(e) {
  this.tagName = e;
}
pi(Qn, dt);
Qn.prototype.serializeTo = function(e) {
  e.appendIndent().append("<" + this.tagName + ">"), this.serializeValue(e), e.append("</" + this.tagName + ">").appendNewLine();
};
function X(e, t) {
  this.body = [], this.attrs = [], this.parent = e, this.propertyDescriptor = t;
}
X.prototype.build = function(e) {
  this.element = e;
  var t = e.$descriptor, n = this.propertyDescriptor, r, a, i = t.isGeneric;
  return i ? r = this.parseGenericNsAttributes(e) : r = this.parseNsAttributes(e), n ? this.ns = this.nsPropertyTagName(n) : this.ns = this.nsTagName(t), this.tagName = this.addTagName(this.ns), i ? this.parseGenericContainments(e) : (a = Zd(e), this.parseAttributes(rp(a)), this.parseContainments(ip(a))), this.parseGenericAttributes(e, r), this;
};
X.prototype.nsTagName = function(e) {
  var t = this.logNamespaceUsed(e.ns);
  return Jd(t, e);
};
X.prototype.nsPropertyTagName = function(e) {
  var t = this.logNamespaceUsed(e.ns);
  return Yd(t, e);
};
X.prototype.isLocalNs = function(e) {
  return e.uri === this.ns.uri;
};
X.prototype.nsAttributeName = function(e) {
  var t;
  if (pt(e) ? t = de(e) : t = e.ns, e.inherited)
    return { localName: t.localName };
  var n = this.logNamespaceUsed(t);
  return this.getNamespaces().logUsed(n), this.isLocalNs(n) ? { localName: t.localName } : Y({ localName: t.localName }, n);
};
X.prototype.parseGenericNsAttributes = function(e) {
  return Object.entries(e).filter(
    ([t, n]) => !t.startsWith("$") && this.parseNsAttribute(e, t, n)
  ).map(
    ([t, n]) => ({ name: t, value: n })
  );
};
X.prototype.parseGenericContainments = function(e) {
  var t = e.$body;
  t && this.body.push(new dt().build({ type: "String" }, t));
  var n = e.$children;
  n && V(n, (r) => {
    this.body.push(new X(this).build(r));
  });
};
X.prototype.parseNsAttribute = function(e, t, n) {
  var r = e.$model, a = de(t), i;
  if (a.prefix === "xmlns" && (i = { prefix: a.localName, uri: n }), !a.prefix && a.localName === "xmlns" && (i = { uri: n }), !i)
    return {
      name: t,
      value: n
    };
  if (r && r.getPackage(n))
    this.logNamespace(i, !0, !0);
  else {
    var s = this.logNamespaceUsed(i, !0);
    this.getNamespaces().logUsed(s);
  }
};
X.prototype.parseNsAttributes = function(e) {
  var t = this, n = e.$attrs, r = [];
  return V(n, function(a, i) {
    var s = t.parseNsAttribute(e, i, a);
    s && r.push(s);
  }), r;
};
X.prototype.parseGenericAttributes = function(e, t) {
  var n = this;
  V(t, function(r) {
    try {
      n.addAttribute(n.nsAttributeName(r.name), r.value);
    } catch (a) {
      typeof console < "u" && console.warn(
        `missing namespace information for <${r.name}=${r.value}> on`,
        e,
        a
      );
    }
  });
};
X.prototype.parseContainments = function(e) {
  var t = this, n = this.body, r = this.element;
  V(e, function(a) {
    var i = r.get(a.name), s = a.isReference, o = a.isMany;
    if (o || (i = [i]), a.isBody)
      n.push(new dt().build(a, i[0]));
    else if (ri(a.type))
      V(i, function(c) {
        n.push(new Qn(t.addTagName(t.nsPropertyTagName(a))).build(a, c));
      });
    else if (s)
      V(i, function(c) {
        n.push(new Xn(t.addTagName(t.nsPropertyTagName(a))).build(c));
      });
    else {
      var l = ci(a);
      V(i, function(c) {
        var u;
        l ? l === li ? u = new X(t, a) : u = new dn(t, a, l) : u = new X(t), n.push(u.build(c));
      });
    }
  });
};
X.prototype.getNamespaces = function(e) {
  var t = this.namespaces, n = this.parent, r;
  return t || (r = n && n.getNamespaces(), e || !r ? this.namespaces = t = new Ve(r) : t = r), t;
};
X.prototype.logNamespace = function(e, t, n) {
  var r = this.getNamespaces(n), a = e.uri, i = e.prefix, s = r.byUri(a);
  return (!s || n) && r.add(e, t), r.mapPrefix(i, a), e;
};
X.prototype.logNamespaceUsed = function(e, t) {
  var n = this.getNamespaces(t), r = e.prefix, a = e.uri, i, s, o;
  if (!r && !a)
    return { localName: e.localName };
  if (o = n.defaultUriByPrefix(r), a = a || o || n.uriByPrefix(r), !a)
    throw new Error("no namespace uri given for prefix <" + r + ">");
  if (e = n.byUri(a), !e && !r && (e = this.logNamespace({ uri: a }, o === a, !0)), !e) {
    for (i = r, s = 1; n.uriByPrefix(i); )
      i = r + "_" + s++;
    e = this.logNamespace({ prefix: i, uri: a }, o === a);
  }
  return r && n.mapPrefix(r, a), e;
};
X.prototype.parseAttributes = function(e) {
  var t = this, n = this.element;
  V(e, function(r) {
    var a = n.get(r.name);
    if (r.isReference)
      if (!r.isMany)
        a = a.id;
      else {
        var i = [];
        V(a, function(s) {
          i.push(s.id);
        }), a = i.join(" ");
      }
    t.addAttribute(t.nsAttributeName(r), a);
  });
};
X.prototype.addTagName = function(e) {
  var t = this.logNamespaceUsed(e);
  return this.getNamespaces().logUsed(t), mi(e);
};
X.prototype.addAttribute = function(e, t) {
  var n = this.attrs;
  pt(t) && (t = np(t));
  var r = Td(n, function(i) {
    return i.name.localName === e.localName && i.name.uri === e.uri && i.name.prefix === e.prefix;
  }), a = { name: e, value: t };
  r !== -1 ? n.splice(r, 1, a) : n.push(a);
};
X.prototype.serializeAttributes = function(e) {
  var t = this.attrs, n = this.namespaces;
  n && (t = Qd(n).concat(t)), V(t, function(r) {
    e.append(" ").append(mi(r.name)).append('="').append(r.value).append('"');
  });
};
X.prototype.serializeTo = function(e) {
  var t = this.body[0], n = t && t.constructor !== dt;
  e.appendIndent().append("<" + this.tagName), this.serializeAttributes(e), e.append(t ? ">" : " />"), t && (n && e.appendNewLine().indent(), V(this.body, function(r) {
    r.serializeTo(e);
  }), n && e.unindent().appendIndent(), e.append("</" + this.tagName + ">")), e.appendNewLine();
};
function dn(e, t, n) {
  X.call(this, e, t), this.serialization = n;
}
pi(dn, X);
dn.prototype.parseNsAttributes = function(e) {
  var t = X.prototype.parseNsAttributes.call(this, e).filter(
    (s) => s.name !== this.serialization
  ), n = e.$descriptor;
  if (n.name === this.propertyDescriptor.type)
    return t;
  var r = this.typeNs = this.nsTagName(n);
  this.getNamespaces().logUsed(this.typeNs);
  var a = e.$model.getPackage(r.uri), i = a.xml && a.xml.typePrefix || "";
  return this.addAttribute(
    this.nsAttributeName(this.serialization),
    (r.prefix ? r.prefix + ":" : "") + i + n.ns.localName
  ), t;
};
dn.prototype.isLocalNs = function(e) {
  return e.uri === (this.typeNs || this.ns).uri;
};
function sp() {
  this.value = "", this.write = function(e) {
    this.value += e;
  };
}
function op(e, t) {
  var n = [""];
  this.append = function(r) {
    return e.write(r), this;
  }, this.appendNewLine = function() {
    return t && e.write(`
`), this;
  }, this.appendIndent = function() {
    return t && e.write(n.join("  ")), this;
  }, this.indent = function() {
    return n.push(""), this;
  }, this.unindent = function() {
    return n.pop(), this;
  };
}
function lp(e) {
  e = Y({ format: !1, preamble: !0 }, e || {});
  function t(n, r) {
    var a = r || new sp(), i = new op(a, e.format);
    e.preamble && i.append(Vd);
    var s = new X(), o = n.$model;
    if (s.getNamespaces().mapDefaultPrefixes(cp(o)), s.build(n).serializeTo(i), !r)
      return a.value;
  }
  return {
    toXML: t
  };
}
function cp(e) {
  const t = e.config && e.config.nsMap || {}, n = {};
  for (const r in Mn)
    n[r] = Mn[r];
  for (const r in t) {
    const a = t[r];
    n[a] = r;
  }
  for (const r of e.getPackages())
    n[r.prefix] = r.uri;
  return n;
}
function pn(e, t) {
  Me.call(this, e, t);
}
pn.prototype = Object.create(Me.prototype);
pn.prototype.fromXML = function(e, t, n) {
  pt(t) || (n = t, t = "bpmn:Definitions");
  var r = new Wn(Y({ model: this, lax: !0 }, n)), a = r.handler(t);
  return r.fromXML(e, a);
};
pn.prototype.toXML = function(e, t) {
  var n = new lp(t);
  return new Promise(function(r, a) {
    try {
      var i = n.toXML(e);
      return r({
        xml: i
      });
    } catch (s) {
      return a(s);
    }
  });
};
var up = "BPMN20", dp = "http://www.omg.org/spec/BPMN/20100524/MODEL", pp = "bpmn", mp = [], hp = [
  {
    name: "Interface",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "operations",
        type: "Operation",
        isMany: !0
      },
      {
        name: "implementationRef",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "Operation",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "inMessageRef",
        type: "Message",
        isReference: !0
      },
      {
        name: "outMessageRef",
        type: "Message",
        isReference: !0
      },
      {
        name: "errorRef",
        type: "Error",
        isMany: !0,
        isReference: !0
      },
      {
        name: "implementationRef",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "EndPoint",
    superClass: [
      "RootElement"
    ]
  },
  {
    name: "Auditing",
    superClass: [
      "BaseElement"
    ]
  },
  {
    name: "GlobalTask",
    superClass: [
      "CallableElement"
    ],
    properties: [
      {
        name: "resources",
        type: "ResourceRole",
        isMany: !0
      }
    ]
  },
  {
    name: "Monitoring",
    superClass: [
      "BaseElement"
    ]
  },
  {
    name: "Performer",
    superClass: [
      "ResourceRole"
    ]
  },
  {
    name: "Process",
    superClass: [
      "FlowElementsContainer",
      "CallableElement"
    ],
    properties: [
      {
        name: "processType",
        type: "ProcessType",
        isAttr: !0
      },
      {
        name: "isClosed",
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "auditing",
        type: "Auditing"
      },
      {
        name: "monitoring",
        type: "Monitoring"
      },
      {
        name: "properties",
        type: "Property",
        isMany: !0
      },
      {
        name: "laneSets",
        isMany: !0,
        replaces: "FlowElementsContainer#laneSets",
        type: "LaneSet"
      },
      {
        name: "flowElements",
        isMany: !0,
        replaces: "FlowElementsContainer#flowElements",
        type: "FlowElement"
      },
      {
        name: "artifacts",
        type: "Artifact",
        isMany: !0
      },
      {
        name: "resources",
        type: "ResourceRole",
        isMany: !0
      },
      {
        name: "correlationSubscriptions",
        type: "CorrelationSubscription",
        isMany: !0
      },
      {
        name: "supports",
        type: "Process",
        isMany: !0,
        isReference: !0
      },
      {
        name: "definitionalCollaborationRef",
        type: "Collaboration",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "isExecutable",
        isAttr: !0,
        type: "Boolean"
      }
    ]
  },
  {
    name: "LaneSet",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "lanes",
        type: "Lane",
        isMany: !0
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "Lane",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "partitionElementRef",
        type: "BaseElement",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "partitionElement",
        type: "BaseElement"
      },
      {
        name: "flowNodeRef",
        type: "FlowNode",
        isMany: !0,
        isReference: !0
      },
      {
        name: "childLaneSet",
        type: "LaneSet",
        xml: {
          serialize: "xsi:type"
        }
      }
    ]
  },
  {
    name: "GlobalManualTask",
    superClass: [
      "GlobalTask"
    ]
  },
  {
    name: "ManualTask",
    superClass: [
      "Task"
    ]
  },
  {
    name: "UserTask",
    superClass: [
      "Task"
    ],
    properties: [
      {
        name: "renderings",
        type: "Rendering",
        isMany: !0
      },
      {
        name: "implementation",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "Rendering",
    superClass: [
      "BaseElement"
    ]
  },
  {
    name: "HumanPerformer",
    superClass: [
      "Performer"
    ]
  },
  {
    name: "PotentialOwner",
    superClass: [
      "HumanPerformer"
    ]
  },
  {
    name: "GlobalUserTask",
    superClass: [
      "GlobalTask"
    ],
    properties: [
      {
        name: "implementation",
        isAttr: !0,
        type: "String"
      },
      {
        name: "renderings",
        type: "Rendering",
        isMany: !0
      }
    ]
  },
  {
    name: "Gateway",
    isAbstract: !0,
    superClass: [
      "FlowNode"
    ],
    properties: [
      {
        name: "gatewayDirection",
        type: "GatewayDirection",
        default: "Unspecified",
        isAttr: !0
      }
    ]
  },
  {
    name: "EventBasedGateway",
    superClass: [
      "Gateway"
    ],
    properties: [
      {
        name: "instantiate",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "eventGatewayType",
        type: "EventBasedGatewayType",
        isAttr: !0,
        default: "Exclusive"
      }
    ]
  },
  {
    name: "ComplexGateway",
    superClass: [
      "Gateway"
    ],
    properties: [
      {
        name: "activationCondition",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      },
      {
        name: "default",
        type: "SequenceFlow",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "ExclusiveGateway",
    superClass: [
      "Gateway"
    ],
    properties: [
      {
        name: "default",
        type: "SequenceFlow",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "InclusiveGateway",
    superClass: [
      "Gateway"
    ],
    properties: [
      {
        name: "default",
        type: "SequenceFlow",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "ParallelGateway",
    superClass: [
      "Gateway"
    ]
  },
  {
    name: "RootElement",
    isAbstract: !0,
    superClass: [
      "BaseElement"
    ]
  },
  {
    name: "Relationship",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "type",
        isAttr: !0,
        type: "String"
      },
      {
        name: "direction",
        type: "RelationshipDirection",
        isAttr: !0
      },
      {
        name: "source",
        isMany: !0,
        isReference: !0,
        type: "Element"
      },
      {
        name: "target",
        isMany: !0,
        isReference: !0,
        type: "Element"
      }
    ]
  },
  {
    name: "BaseElement",
    isAbstract: !0,
    properties: [
      {
        name: "id",
        isAttr: !0,
        type: "String",
        isId: !0
      },
      {
        name: "documentation",
        type: "Documentation",
        isMany: !0
      },
      {
        name: "extensionDefinitions",
        type: "ExtensionDefinition",
        isMany: !0,
        isReference: !0
      },
      {
        name: "extensionElements",
        type: "ExtensionElements"
      }
    ]
  },
  {
    name: "Extension",
    properties: [
      {
        name: "mustUnderstand",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "definition",
        type: "ExtensionDefinition",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "ExtensionDefinition",
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "extensionAttributeDefinitions",
        type: "ExtensionAttributeDefinition",
        isMany: !0
      }
    ]
  },
  {
    name: "ExtensionAttributeDefinition",
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "type",
        isAttr: !0,
        type: "String"
      },
      {
        name: "isReference",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "extensionDefinition",
        type: "ExtensionDefinition",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "ExtensionElements",
    properties: [
      {
        name: "valueRef",
        isAttr: !0,
        isReference: !0,
        type: "Element"
      },
      {
        name: "values",
        type: "Element",
        isMany: !0
      },
      {
        name: "extensionAttributeDefinition",
        type: "ExtensionAttributeDefinition",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Documentation",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "text",
        type: "String",
        isBody: !0
      },
      {
        name: "textFormat",
        default: "text/plain",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "Event",
    isAbstract: !0,
    superClass: [
      "FlowNode",
      "InteractionNode"
    ],
    properties: [
      {
        name: "properties",
        type: "Property",
        isMany: !0
      }
    ]
  },
  {
    name: "IntermediateCatchEvent",
    superClass: [
      "CatchEvent"
    ]
  },
  {
    name: "IntermediateThrowEvent",
    superClass: [
      "ThrowEvent"
    ]
  },
  {
    name: "EndEvent",
    superClass: [
      "ThrowEvent"
    ]
  },
  {
    name: "StartEvent",
    superClass: [
      "CatchEvent"
    ],
    properties: [
      {
        name: "isInterrupting",
        default: !0,
        isAttr: !0,
        type: "Boolean"
      }
    ]
  },
  {
    name: "ThrowEvent",
    isAbstract: !0,
    superClass: [
      "Event"
    ],
    properties: [
      {
        name: "dataInputs",
        type: "DataInput",
        isMany: !0
      },
      {
        name: "dataInputAssociations",
        type: "DataInputAssociation",
        isMany: !0
      },
      {
        name: "inputSet",
        type: "InputSet"
      },
      {
        name: "eventDefinitions",
        type: "EventDefinition",
        isMany: !0
      },
      {
        name: "eventDefinitionRef",
        type: "EventDefinition",
        isMany: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "CatchEvent",
    isAbstract: !0,
    superClass: [
      "Event"
    ],
    properties: [
      {
        name: "parallelMultiple",
        isAttr: !0,
        type: "Boolean",
        default: !1
      },
      {
        name: "dataOutputs",
        type: "DataOutput",
        isMany: !0
      },
      {
        name: "dataOutputAssociations",
        type: "DataOutputAssociation",
        isMany: !0
      },
      {
        name: "outputSet",
        type: "OutputSet"
      },
      {
        name: "eventDefinitions",
        type: "EventDefinition",
        isMany: !0
      },
      {
        name: "eventDefinitionRef",
        type: "EventDefinition",
        isMany: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "BoundaryEvent",
    superClass: [
      "CatchEvent"
    ],
    properties: [
      {
        name: "cancelActivity",
        default: !0,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "attachedToRef",
        type: "Activity",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "EventDefinition",
    isAbstract: !0,
    superClass: [
      "RootElement"
    ]
  },
  {
    name: "CancelEventDefinition",
    superClass: [
      "EventDefinition"
    ]
  },
  {
    name: "ErrorEventDefinition",
    superClass: [
      "EventDefinition"
    ],
    properties: [
      {
        name: "errorRef",
        type: "Error",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "TerminateEventDefinition",
    superClass: [
      "EventDefinition"
    ]
  },
  {
    name: "EscalationEventDefinition",
    superClass: [
      "EventDefinition"
    ],
    properties: [
      {
        name: "escalationRef",
        type: "Escalation",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Escalation",
    properties: [
      {
        name: "structureRef",
        type: "ItemDefinition",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "escalationCode",
        isAttr: !0,
        type: "String"
      }
    ],
    superClass: [
      "RootElement"
    ]
  },
  {
    name: "CompensateEventDefinition",
    superClass: [
      "EventDefinition"
    ],
    properties: [
      {
        name: "waitForCompletion",
        isAttr: !0,
        type: "Boolean",
        default: !0
      },
      {
        name: "activityRef",
        type: "Activity",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "TimerEventDefinition",
    superClass: [
      "EventDefinition"
    ],
    properties: [
      {
        name: "timeDate",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      },
      {
        name: "timeCycle",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      },
      {
        name: "timeDuration",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      }
    ]
  },
  {
    name: "LinkEventDefinition",
    superClass: [
      "EventDefinition"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "target",
        type: "LinkEventDefinition",
        isReference: !0
      },
      {
        name: "source",
        type: "LinkEventDefinition",
        isMany: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "MessageEventDefinition",
    superClass: [
      "EventDefinition"
    ],
    properties: [
      {
        name: "messageRef",
        type: "Message",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "operationRef",
        type: "Operation",
        isReference: !0
      }
    ]
  },
  {
    name: "ConditionalEventDefinition",
    superClass: [
      "EventDefinition"
    ],
    properties: [
      {
        name: "condition",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      }
    ]
  },
  {
    name: "SignalEventDefinition",
    superClass: [
      "EventDefinition"
    ],
    properties: [
      {
        name: "signalRef",
        type: "Signal",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Signal",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "structureRef",
        type: "ItemDefinition",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "ImplicitThrowEvent",
    superClass: [
      "ThrowEvent"
    ]
  },
  {
    name: "DataState",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "ItemAwareElement",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "itemSubjectRef",
        type: "ItemDefinition",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "dataState",
        type: "DataState"
      }
    ]
  },
  {
    name: "DataAssociation",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "sourceRef",
        type: "ItemAwareElement",
        isMany: !0,
        isReference: !0
      },
      {
        name: "targetRef",
        type: "ItemAwareElement",
        isReference: !0
      },
      {
        name: "transformation",
        type: "FormalExpression",
        xml: {
          serialize: "property"
        }
      },
      {
        name: "assignment",
        type: "Assignment",
        isMany: !0
      }
    ]
  },
  {
    name: "DataInput",
    superClass: [
      "ItemAwareElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "isCollection",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "inputSetRef",
        type: "InputSet",
        isMany: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "inputSetWithOptional",
        type: "InputSet",
        isMany: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "inputSetWithWhileExecuting",
        type: "InputSet",
        isMany: !0,
        isVirtual: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "DataOutput",
    superClass: [
      "ItemAwareElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "isCollection",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "outputSetRef",
        type: "OutputSet",
        isMany: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "outputSetWithOptional",
        type: "OutputSet",
        isMany: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "outputSetWithWhileExecuting",
        type: "OutputSet",
        isMany: !0,
        isVirtual: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "InputSet",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "dataInputRefs",
        type: "DataInput",
        isMany: !0,
        isReference: !0
      },
      {
        name: "optionalInputRefs",
        type: "DataInput",
        isMany: !0,
        isReference: !0
      },
      {
        name: "whileExecutingInputRefs",
        type: "DataInput",
        isMany: !0,
        isReference: !0
      },
      {
        name: "outputSetRefs",
        type: "OutputSet",
        isMany: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "OutputSet",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "dataOutputRefs",
        type: "DataOutput",
        isMany: !0,
        isReference: !0
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "inputSetRefs",
        type: "InputSet",
        isMany: !0,
        isReference: !0
      },
      {
        name: "optionalOutputRefs",
        type: "DataOutput",
        isMany: !0,
        isReference: !0
      },
      {
        name: "whileExecutingOutputRefs",
        type: "DataOutput",
        isMany: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Property",
    superClass: [
      "ItemAwareElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "DataInputAssociation",
    superClass: [
      "DataAssociation"
    ]
  },
  {
    name: "DataOutputAssociation",
    superClass: [
      "DataAssociation"
    ]
  },
  {
    name: "InputOutputSpecification",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "dataInputs",
        type: "DataInput",
        isMany: !0
      },
      {
        name: "dataOutputs",
        type: "DataOutput",
        isMany: !0
      },
      {
        name: "inputSets",
        type: "InputSet",
        isMany: !0
      },
      {
        name: "outputSets",
        type: "OutputSet",
        isMany: !0
      }
    ]
  },
  {
    name: "DataObject",
    superClass: [
      "FlowElement",
      "ItemAwareElement"
    ],
    properties: [
      {
        name: "isCollection",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      }
    ]
  },
  {
    name: "InputOutputBinding",
    properties: [
      {
        name: "inputDataRef",
        type: "InputSet",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "outputDataRef",
        type: "OutputSet",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "operationRef",
        type: "Operation",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Assignment",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "from",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      },
      {
        name: "to",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      }
    ]
  },
  {
    name: "DataStore",
    superClass: [
      "RootElement",
      "ItemAwareElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "capacity",
        isAttr: !0,
        type: "Integer"
      },
      {
        name: "isUnlimited",
        default: !0,
        isAttr: !0,
        type: "Boolean"
      }
    ]
  },
  {
    name: "DataStoreReference",
    superClass: [
      "ItemAwareElement",
      "FlowElement"
    ],
    properties: [
      {
        name: "dataStoreRef",
        type: "DataStore",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "DataObjectReference",
    superClass: [
      "ItemAwareElement",
      "FlowElement"
    ],
    properties: [
      {
        name: "dataObjectRef",
        type: "DataObject",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "ConversationLink",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "sourceRef",
        type: "InteractionNode",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "targetRef",
        type: "InteractionNode",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "ConversationAssociation",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "innerConversationNodeRef",
        type: "ConversationNode",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "outerConversationNodeRef",
        type: "ConversationNode",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "CallConversation",
    superClass: [
      "ConversationNode"
    ],
    properties: [
      {
        name: "calledCollaborationRef",
        type: "Collaboration",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "participantAssociations",
        type: "ParticipantAssociation",
        isMany: !0
      }
    ]
  },
  {
    name: "Conversation",
    superClass: [
      "ConversationNode"
    ]
  },
  {
    name: "SubConversation",
    superClass: [
      "ConversationNode"
    ],
    properties: [
      {
        name: "conversationNodes",
        type: "ConversationNode",
        isMany: !0
      }
    ]
  },
  {
    name: "ConversationNode",
    isAbstract: !0,
    superClass: [
      "InteractionNode",
      "BaseElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "participantRef",
        type: "Participant",
        isMany: !0,
        isReference: !0
      },
      {
        name: "messageFlowRefs",
        type: "MessageFlow",
        isMany: !0,
        isReference: !0
      },
      {
        name: "correlationKeys",
        type: "CorrelationKey",
        isMany: !0
      }
    ]
  },
  {
    name: "GlobalConversation",
    superClass: [
      "Collaboration"
    ]
  },
  {
    name: "PartnerEntity",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "participantRef",
        type: "Participant",
        isMany: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "PartnerRole",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "participantRef",
        type: "Participant",
        isMany: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "CorrelationProperty",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "correlationPropertyRetrievalExpression",
        type: "CorrelationPropertyRetrievalExpression",
        isMany: !0
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "type",
        type: "ItemDefinition",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Error",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "structureRef",
        type: "ItemDefinition",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "errorCode",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "CorrelationKey",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "correlationPropertyRef",
        type: "CorrelationProperty",
        isMany: !0,
        isReference: !0
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "Expression",
    superClass: [
      "BaseElement"
    ],
    isAbstract: !1,
    properties: [
      {
        name: "body",
        isBody: !0,
        type: "String"
      }
    ]
  },
  {
    name: "FormalExpression",
    superClass: [
      "Expression"
    ],
    properties: [
      {
        name: "language",
        isAttr: !0,
        type: "String"
      },
      {
        name: "evaluatesToTypeRef",
        type: "ItemDefinition",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Message",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "itemRef",
        type: "ItemDefinition",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "ItemDefinition",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "itemKind",
        type: "ItemKind",
        isAttr: !0
      },
      {
        name: "structureRef",
        isAttr: !0,
        type: "String"
      },
      {
        name: "isCollection",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "import",
        type: "Import",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "FlowElement",
    isAbstract: !0,
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "auditing",
        type: "Auditing"
      },
      {
        name: "monitoring",
        type: "Monitoring"
      },
      {
        name: "categoryValueRef",
        type: "CategoryValue",
        isMany: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "SequenceFlow",
    superClass: [
      "FlowElement"
    ],
    properties: [
      {
        name: "isImmediate",
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "conditionExpression",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      },
      {
        name: "sourceRef",
        type: "FlowNode",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "targetRef",
        type: "FlowNode",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "FlowElementsContainer",
    isAbstract: !0,
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "laneSets",
        type: "LaneSet",
        isMany: !0
      },
      {
        name: "flowElements",
        type: "FlowElement",
        isMany: !0
      }
    ]
  },
  {
    name: "CallableElement",
    isAbstract: !0,
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "ioSpecification",
        type: "InputOutputSpecification",
        xml: {
          serialize: "property"
        }
      },
      {
        name: "supportedInterfaceRef",
        type: "Interface",
        isMany: !0,
        isReference: !0
      },
      {
        name: "ioBinding",
        type: "InputOutputBinding",
        isMany: !0,
        xml: {
          serialize: "property"
        }
      }
    ]
  },
  {
    name: "FlowNode",
    isAbstract: !0,
    superClass: [
      "FlowElement"
    ],
    properties: [
      {
        name: "incoming",
        type: "SequenceFlow",
        isMany: !0,
        isReference: !0
      },
      {
        name: "outgoing",
        type: "SequenceFlow",
        isMany: !0,
        isReference: !0
      },
      {
        name: "lanes",
        type: "Lane",
        isMany: !0,
        isVirtual: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "CorrelationPropertyRetrievalExpression",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "messagePath",
        type: "FormalExpression"
      },
      {
        name: "messageRef",
        type: "Message",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "CorrelationPropertyBinding",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "dataPath",
        type: "FormalExpression"
      },
      {
        name: "correlationPropertyRef",
        type: "CorrelationProperty",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Resource",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "resourceParameters",
        type: "ResourceParameter",
        isMany: !0
      }
    ]
  },
  {
    name: "ResourceParameter",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "isRequired",
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "type",
        type: "ItemDefinition",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "CorrelationSubscription",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "correlationKeyRef",
        type: "CorrelationKey",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "correlationPropertyBinding",
        type: "CorrelationPropertyBinding",
        isMany: !0
      }
    ]
  },
  {
    name: "MessageFlow",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "sourceRef",
        type: "InteractionNode",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "targetRef",
        type: "InteractionNode",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "messageRef",
        type: "Message",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "MessageFlowAssociation",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "innerMessageFlowRef",
        type: "MessageFlow",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "outerMessageFlowRef",
        type: "MessageFlow",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "InteractionNode",
    isAbstract: !0,
    properties: [
      {
        name: "incomingConversationLinks",
        type: "ConversationLink",
        isMany: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "outgoingConversationLinks",
        type: "ConversationLink",
        isMany: !0,
        isVirtual: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Participant",
    superClass: [
      "InteractionNode",
      "BaseElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "interfaceRef",
        type: "Interface",
        isMany: !0,
        isReference: !0
      },
      {
        name: "participantMultiplicity",
        type: "ParticipantMultiplicity"
      },
      {
        name: "endPointRefs",
        type: "EndPoint",
        isMany: !0,
        isReference: !0
      },
      {
        name: "processRef",
        type: "Process",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "ParticipantAssociation",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "innerParticipantRef",
        type: "Participant",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "outerParticipantRef",
        type: "Participant",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "ParticipantMultiplicity",
    properties: [
      {
        name: "minimum",
        default: 0,
        isAttr: !0,
        type: "Integer"
      },
      {
        name: "maximum",
        default: 1,
        isAttr: !0,
        type: "Integer"
      }
    ],
    superClass: [
      "BaseElement"
    ]
  },
  {
    name: "Collaboration",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "isClosed",
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "participants",
        type: "Participant",
        isMany: !0
      },
      {
        name: "messageFlows",
        type: "MessageFlow",
        isMany: !0
      },
      {
        name: "artifacts",
        type: "Artifact",
        isMany: !0
      },
      {
        name: "conversations",
        type: "ConversationNode",
        isMany: !0
      },
      {
        name: "conversationAssociations",
        type: "ConversationAssociation"
      },
      {
        name: "participantAssociations",
        type: "ParticipantAssociation",
        isMany: !0
      },
      {
        name: "messageFlowAssociations",
        type: "MessageFlowAssociation",
        isMany: !0
      },
      {
        name: "correlationKeys",
        type: "CorrelationKey",
        isMany: !0
      },
      {
        name: "choreographyRef",
        type: "Choreography",
        isMany: !0,
        isReference: !0
      },
      {
        name: "conversationLinks",
        type: "ConversationLink",
        isMany: !0
      }
    ]
  },
  {
    name: "ChoreographyActivity",
    isAbstract: !0,
    superClass: [
      "FlowNode"
    ],
    properties: [
      {
        name: "participantRef",
        type: "Participant",
        isMany: !0,
        isReference: !0
      },
      {
        name: "initiatingParticipantRef",
        type: "Participant",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "correlationKeys",
        type: "CorrelationKey",
        isMany: !0
      },
      {
        name: "loopType",
        type: "ChoreographyLoopType",
        default: "None",
        isAttr: !0
      }
    ]
  },
  {
    name: "CallChoreography",
    superClass: [
      "ChoreographyActivity"
    ],
    properties: [
      {
        name: "calledChoreographyRef",
        type: "Choreography",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "participantAssociations",
        type: "ParticipantAssociation",
        isMany: !0
      }
    ]
  },
  {
    name: "SubChoreography",
    superClass: [
      "ChoreographyActivity",
      "FlowElementsContainer"
    ],
    properties: [
      {
        name: "artifacts",
        type: "Artifact",
        isMany: !0
      }
    ]
  },
  {
    name: "ChoreographyTask",
    superClass: [
      "ChoreographyActivity"
    ],
    properties: [
      {
        name: "messageFlowRef",
        type: "MessageFlow",
        isMany: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Choreography",
    superClass: [
      "Collaboration",
      "FlowElementsContainer"
    ]
  },
  {
    name: "GlobalChoreographyTask",
    superClass: [
      "Choreography"
    ],
    properties: [
      {
        name: "initiatingParticipantRef",
        type: "Participant",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "TextAnnotation",
    superClass: [
      "Artifact"
    ],
    properties: [
      {
        name: "text",
        type: "String"
      },
      {
        name: "textFormat",
        default: "text/plain",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "Group",
    superClass: [
      "Artifact"
    ],
    properties: [
      {
        name: "categoryValueRef",
        type: "CategoryValue",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Association",
    superClass: [
      "Artifact"
    ],
    properties: [
      {
        name: "associationDirection",
        type: "AssociationDirection",
        isAttr: !0
      },
      {
        name: "sourceRef",
        type: "BaseElement",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "targetRef",
        type: "BaseElement",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "Category",
    superClass: [
      "RootElement"
    ],
    properties: [
      {
        name: "categoryValue",
        type: "CategoryValue",
        isMany: !0
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "Artifact",
    isAbstract: !0,
    superClass: [
      "BaseElement"
    ]
  },
  {
    name: "CategoryValue",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "categorizedFlowElements",
        type: "FlowElement",
        isMany: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "value",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "Activity",
    isAbstract: !0,
    superClass: [
      "FlowNode"
    ],
    properties: [
      {
        name: "isForCompensation",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "default",
        type: "SequenceFlow",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "ioSpecification",
        type: "InputOutputSpecification",
        xml: {
          serialize: "property"
        }
      },
      {
        name: "boundaryEventRefs",
        type: "BoundaryEvent",
        isMany: !0,
        isReference: !0
      },
      {
        name: "properties",
        type: "Property",
        isMany: !0
      },
      {
        name: "dataInputAssociations",
        type: "DataInputAssociation",
        isMany: !0
      },
      {
        name: "dataOutputAssociations",
        type: "DataOutputAssociation",
        isMany: !0
      },
      {
        name: "startQuantity",
        default: 1,
        isAttr: !0,
        type: "Integer"
      },
      {
        name: "resources",
        type: "ResourceRole",
        isMany: !0
      },
      {
        name: "completionQuantity",
        default: 1,
        isAttr: !0,
        type: "Integer"
      },
      {
        name: "loopCharacteristics",
        type: "LoopCharacteristics"
      }
    ]
  },
  {
    name: "ServiceTask",
    superClass: [
      "Task"
    ],
    properties: [
      {
        name: "implementation",
        isAttr: !0,
        type: "String"
      },
      {
        name: "operationRef",
        type: "Operation",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "SubProcess",
    superClass: [
      "Activity",
      "FlowElementsContainer",
      "InteractionNode"
    ],
    properties: [
      {
        name: "triggeredByEvent",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "artifacts",
        type: "Artifact",
        isMany: !0
      }
    ]
  },
  {
    name: "LoopCharacteristics",
    isAbstract: !0,
    superClass: [
      "BaseElement"
    ]
  },
  {
    name: "MultiInstanceLoopCharacteristics",
    superClass: [
      "LoopCharacteristics"
    ],
    properties: [
      {
        name: "isSequential",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "behavior",
        type: "MultiInstanceBehavior",
        default: "All",
        isAttr: !0
      },
      {
        name: "loopCardinality",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      },
      {
        name: "loopDataInputRef",
        type: "ItemAwareElement",
        isReference: !0
      },
      {
        name: "loopDataOutputRef",
        type: "ItemAwareElement",
        isReference: !0
      },
      {
        name: "inputDataItem",
        type: "DataInput",
        xml: {
          serialize: "property"
        }
      },
      {
        name: "outputDataItem",
        type: "DataOutput",
        xml: {
          serialize: "property"
        }
      },
      {
        name: "complexBehaviorDefinition",
        type: "ComplexBehaviorDefinition",
        isMany: !0
      },
      {
        name: "completionCondition",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      },
      {
        name: "oneBehaviorEventRef",
        type: "EventDefinition",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "noneBehaviorEventRef",
        type: "EventDefinition",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "StandardLoopCharacteristics",
    superClass: [
      "LoopCharacteristics"
    ],
    properties: [
      {
        name: "testBefore",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "loopCondition",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      },
      {
        name: "loopMaximum",
        type: "Integer",
        isAttr: !0
      }
    ]
  },
  {
    name: "CallActivity",
    superClass: [
      "Activity",
      "InteractionNode"
    ],
    properties: [
      {
        name: "calledElement",
        type: "String",
        isAttr: !0
      }
    ]
  },
  {
    name: "Task",
    superClass: [
      "Activity",
      "InteractionNode"
    ]
  },
  {
    name: "SendTask",
    superClass: [
      "Task"
    ],
    properties: [
      {
        name: "implementation",
        isAttr: !0,
        type: "String"
      },
      {
        name: "operationRef",
        type: "Operation",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "messageRef",
        type: "Message",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "ReceiveTask",
    superClass: [
      "Task"
    ],
    properties: [
      {
        name: "implementation",
        isAttr: !0,
        type: "String"
      },
      {
        name: "instantiate",
        default: !1,
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "operationRef",
        type: "Operation",
        isAttr: !0,
        isReference: !0
      },
      {
        name: "messageRef",
        type: "Message",
        isAttr: !0,
        isReference: !0
      }
    ]
  },
  {
    name: "ScriptTask",
    superClass: [
      "Task"
    ],
    properties: [
      {
        name: "scriptFormat",
        isAttr: !0,
        type: "String"
      },
      {
        name: "script",
        type: "String"
      }
    ]
  },
  {
    name: "BusinessRuleTask",
    superClass: [
      "Task"
    ],
    properties: [
      {
        name: "implementation",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "AdHocSubProcess",
    superClass: [
      "SubProcess"
    ],
    properties: [
      {
        name: "completionCondition",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      },
      {
        name: "ordering",
        type: "AdHocOrdering",
        isAttr: !0
      },
      {
        name: "cancelRemainingInstances",
        default: !0,
        isAttr: !0,
        type: "Boolean"
      }
    ]
  },
  {
    name: "Transaction",
    superClass: [
      "SubProcess"
    ],
    properties: [
      {
        name: "protocol",
        isAttr: !0,
        type: "String"
      },
      {
        name: "method",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "GlobalScriptTask",
    superClass: [
      "GlobalTask"
    ],
    properties: [
      {
        name: "scriptLanguage",
        isAttr: !0,
        type: "String"
      },
      {
        name: "script",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "GlobalBusinessRuleTask",
    superClass: [
      "GlobalTask"
    ],
    properties: [
      {
        name: "implementation",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "ComplexBehaviorDefinition",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "condition",
        type: "FormalExpression"
      },
      {
        name: "event",
        type: "ImplicitThrowEvent"
      }
    ]
  },
  {
    name: "ResourceRole",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "resourceRef",
        type: "Resource",
        isReference: !0
      },
      {
        name: "resourceParameterBindings",
        type: "ResourceParameterBinding",
        isMany: !0
      },
      {
        name: "resourceAssignmentExpression",
        type: "ResourceAssignmentExpression"
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "ResourceParameterBinding",
    properties: [
      {
        name: "expression",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      },
      {
        name: "parameterRef",
        type: "ResourceParameter",
        isAttr: !0,
        isReference: !0
      }
    ],
    superClass: [
      "BaseElement"
    ]
  },
  {
    name: "ResourceAssignmentExpression",
    properties: [
      {
        name: "expression",
        type: "Expression",
        xml: {
          serialize: "xsi:type"
        }
      }
    ],
    superClass: [
      "BaseElement"
    ]
  },
  {
    name: "Import",
    properties: [
      {
        name: "importType",
        isAttr: !0,
        type: "String"
      },
      {
        name: "location",
        isAttr: !0,
        type: "String"
      },
      {
        name: "namespace",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "Definitions",
    superClass: [
      "BaseElement"
    ],
    properties: [
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "targetNamespace",
        isAttr: !0,
        type: "String"
      },
      {
        name: "expressionLanguage",
        default: "http://www.w3.org/1999/XPath",
        isAttr: !0,
        type: "String"
      },
      {
        name: "typeLanguage",
        default: "http://www.w3.org/2001/XMLSchema",
        isAttr: !0,
        type: "String"
      },
      {
        name: "imports",
        type: "Import",
        isMany: !0
      },
      {
        name: "extensions",
        type: "Extension",
        isMany: !0
      },
      {
        name: "rootElements",
        type: "RootElement",
        isMany: !0
      },
      {
        name: "diagrams",
        isMany: !0,
        type: "bpmndi:BPMNDiagram"
      },
      {
        name: "exporter",
        isAttr: !0,
        type: "String"
      },
      {
        name: "relationships",
        type: "Relationship",
        isMany: !0
      },
      {
        name: "exporterVersion",
        isAttr: !0,
        type: "String"
      }
    ]
  }
], fp = [
  {
    name: "ProcessType",
    literalValues: [
      {
        name: "None"
      },
      {
        name: "Public"
      },
      {
        name: "Private"
      }
    ]
  },
  {
    name: "GatewayDirection",
    literalValues: [
      {
        name: "Unspecified"
      },
      {
        name: "Converging"
      },
      {
        name: "Diverging"
      },
      {
        name: "Mixed"
      }
    ]
  },
  {
    name: "EventBasedGatewayType",
    literalValues: [
      {
        name: "Parallel"
      },
      {
        name: "Exclusive"
      }
    ]
  },
  {
    name: "RelationshipDirection",
    literalValues: [
      {
        name: "None"
      },
      {
        name: "Forward"
      },
      {
        name: "Backward"
      },
      {
        name: "Both"
      }
    ]
  },
  {
    name: "ItemKind",
    literalValues: [
      {
        name: "Physical"
      },
      {
        name: "Information"
      }
    ]
  },
  {
    name: "ChoreographyLoopType",
    literalValues: [
      {
        name: "None"
      },
      {
        name: "Standard"
      },
      {
        name: "MultiInstanceSequential"
      },
      {
        name: "MultiInstanceParallel"
      }
    ]
  },
  {
    name: "AssociationDirection",
    literalValues: [
      {
        name: "None"
      },
      {
        name: "One"
      },
      {
        name: "Both"
      }
    ]
  },
  {
    name: "MultiInstanceBehavior",
    literalValues: [
      {
        name: "None"
      },
      {
        name: "One"
      },
      {
        name: "All"
      },
      {
        name: "Complex"
      }
    ]
  },
  {
    name: "AdHocOrdering",
    literalValues: [
      {
        name: "Parallel"
      },
      {
        name: "Sequential"
      }
    ]
  }
], gp = {
  tagAlias: "lowerCase",
  typePrefix: "t"
}, yp = {
  name: up,
  uri: dp,
  prefix: pp,
  associations: mp,
  types: hp,
  enumerations: fp,
  xml: gp
}, bp = "BPMNDI", vp = "http://www.omg.org/spec/BPMN/20100524/DI", kp = "bpmndi", wp = [
  {
    name: "BPMNDiagram",
    properties: [
      {
        name: "plane",
        type: "BPMNPlane",
        redefines: "di:Diagram#rootElement"
      },
      {
        name: "labelStyle",
        type: "BPMNLabelStyle",
        isMany: !0
      }
    ],
    superClass: [
      "di:Diagram"
    ]
  },
  {
    name: "BPMNPlane",
    properties: [
      {
        name: "bpmnElement",
        isAttr: !0,
        isReference: !0,
        type: "bpmn:BaseElement",
        redefines: "di:DiagramElement#modelElement"
      }
    ],
    superClass: [
      "di:Plane"
    ]
  },
  {
    name: "BPMNShape",
    properties: [
      {
        name: "bpmnElement",
        isAttr: !0,
        isReference: !0,
        type: "bpmn:BaseElement",
        redefines: "di:DiagramElement#modelElement"
      },
      {
        name: "isHorizontal",
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "isExpanded",
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "isMarkerVisible",
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "label",
        type: "BPMNLabel"
      },
      {
        name: "isMessageVisible",
        isAttr: !0,
        type: "Boolean"
      },
      {
        name: "participantBandKind",
        type: "ParticipantBandKind",
        isAttr: !0
      },
      {
        name: "choreographyActivityShape",
        type: "BPMNShape",
        isAttr: !0,
        isReference: !0
      }
    ],
    superClass: [
      "di:LabeledShape"
    ]
  },
  {
    name: "BPMNEdge",
    properties: [
      {
        name: "label",
        type: "BPMNLabel"
      },
      {
        name: "bpmnElement",
        isAttr: !0,
        isReference: !0,
        type: "bpmn:BaseElement",
        redefines: "di:DiagramElement#modelElement"
      },
      {
        name: "sourceElement",
        isAttr: !0,
        isReference: !0,
        type: "di:DiagramElement",
        redefines: "di:Edge#source"
      },
      {
        name: "targetElement",
        isAttr: !0,
        isReference: !0,
        type: "di:DiagramElement",
        redefines: "di:Edge#target"
      },
      {
        name: "messageVisibleKind",
        type: "MessageVisibleKind",
        isAttr: !0,
        default: "initiating"
      }
    ],
    superClass: [
      "di:LabeledEdge"
    ]
  },
  {
    name: "BPMNLabel",
    properties: [
      {
        name: "labelStyle",
        type: "BPMNLabelStyle",
        isAttr: !0,
        isReference: !0,
        redefines: "di:DiagramElement#style"
      }
    ],
    superClass: [
      "di:Label"
    ]
  },
  {
    name: "BPMNLabelStyle",
    properties: [
      {
        name: "font",
        type: "dc:Font"
      }
    ],
    superClass: [
      "di:Style"
    ]
  }
], xp = [
  {
    name: "ParticipantBandKind",
    literalValues: [
      {
        name: "top_initiating"
      },
      {
        name: "middle_initiating"
      },
      {
        name: "bottom_initiating"
      },
      {
        name: "top_non_initiating"
      },
      {
        name: "middle_non_initiating"
      },
      {
        name: "bottom_non_initiating"
      }
    ]
  },
  {
    name: "MessageVisibleKind",
    literalValues: [
      {
        name: "initiating"
      },
      {
        name: "non_initiating"
      }
    ]
  }
], Ep = [], _p = {
  name: bp,
  uri: vp,
  prefix: kp,
  types: wp,
  enumerations: xp,
  associations: Ep
}, Ip = "DC", Cp = "http://www.omg.org/spec/DD/20100524/DC", Tp = "dc", Sp = [
  {
    name: "Boolean"
  },
  {
    name: "Integer"
  },
  {
    name: "Real"
  },
  {
    name: "String"
  },
  {
    name: "Font",
    properties: [
      {
        name: "name",
        type: "String",
        isAttr: !0
      },
      {
        name: "size",
        type: "Real",
        isAttr: !0
      },
      {
        name: "isBold",
        type: "Boolean",
        isAttr: !0
      },
      {
        name: "isItalic",
        type: "Boolean",
        isAttr: !0
      },
      {
        name: "isUnderline",
        type: "Boolean",
        isAttr: !0
      },
      {
        name: "isStrikeThrough",
        type: "Boolean",
        isAttr: !0
      }
    ]
  },
  {
    name: "Point",
    properties: [
      {
        name: "x",
        type: "Real",
        default: "0",
        isAttr: !0
      },
      {
        name: "y",
        type: "Real",
        default: "0",
        isAttr: !0
      }
    ]
  },
  {
    name: "Bounds",
    properties: [
      {
        name: "x",
        type: "Real",
        default: "0",
        isAttr: !0
      },
      {
        name: "y",
        type: "Real",
        default: "0",
        isAttr: !0
      },
      {
        name: "width",
        type: "Real",
        isAttr: !0
      },
      {
        name: "height",
        type: "Real",
        isAttr: !0
      }
    ]
  }
], Rp = [], Np = {
  name: Ip,
  uri: Cp,
  prefix: Tp,
  types: Sp,
  associations: Rp
}, Ap = "DI", Mp = "http://www.omg.org/spec/DD/20100524/DI", Op = "di", Pp = [
  {
    name: "DiagramElement",
    isAbstract: !0,
    properties: [
      {
        name: "id",
        isAttr: !0,
        isId: !0,
        type: "String"
      },
      {
        name: "extension",
        type: "Extension"
      },
      {
        name: "owningDiagram",
        type: "Diagram",
        isReadOnly: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "owningElement",
        type: "DiagramElement",
        isReadOnly: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "modelElement",
        isReadOnly: !0,
        isVirtual: !0,
        isReference: !0,
        type: "Element"
      },
      {
        name: "style",
        type: "Style",
        isReadOnly: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "ownedElement",
        type: "DiagramElement",
        isReadOnly: !0,
        isMany: !0,
        isVirtual: !0
      }
    ]
  },
  {
    name: "Node",
    isAbstract: !0,
    superClass: [
      "DiagramElement"
    ]
  },
  {
    name: "Edge",
    isAbstract: !0,
    superClass: [
      "DiagramElement"
    ],
    properties: [
      {
        name: "source",
        type: "DiagramElement",
        isReadOnly: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "target",
        type: "DiagramElement",
        isReadOnly: !0,
        isVirtual: !0,
        isReference: !0
      },
      {
        name: "waypoint",
        isUnique: !1,
        isMany: !0,
        type: "dc:Point",
        xml: {
          serialize: "xsi:type"
        }
      }
    ]
  },
  {
    name: "Diagram",
    isAbstract: !0,
    properties: [
      {
        name: "id",
        isAttr: !0,
        isId: !0,
        type: "String"
      },
      {
        name: "rootElement",
        type: "DiagramElement",
        isReadOnly: !0,
        isVirtual: !0
      },
      {
        name: "name",
        isAttr: !0,
        type: "String"
      },
      {
        name: "documentation",
        isAttr: !0,
        type: "String"
      },
      {
        name: "resolution",
        isAttr: !0,
        type: "Real"
      },
      {
        name: "ownedStyle",
        type: "Style",
        isReadOnly: !0,
        isMany: !0,
        isVirtual: !0
      }
    ]
  },
  {
    name: "Shape",
    isAbstract: !0,
    superClass: [
      "Node"
    ],
    properties: [
      {
        name: "bounds",
        type: "dc:Bounds"
      }
    ]
  },
  {
    name: "Plane",
    isAbstract: !0,
    superClass: [
      "Node"
    ],
    properties: [
      {
        name: "planeElement",
        type: "DiagramElement",
        subsettedProperty: "DiagramElement-ownedElement",
        isMany: !0
      }
    ]
  },
  {
    name: "LabeledEdge",
    isAbstract: !0,
    superClass: [
      "Edge"
    ],
    properties: [
      {
        name: "ownedLabel",
        type: "Label",
        isReadOnly: !0,
        subsettedProperty: "DiagramElement-ownedElement",
        isMany: !0,
        isVirtual: !0
      }
    ]
  },
  {
    name: "LabeledShape",
    isAbstract: !0,
    superClass: [
      "Shape"
    ],
    properties: [
      {
        name: "ownedLabel",
        type: "Label",
        isReadOnly: !0,
        subsettedProperty: "DiagramElement-ownedElement",
        isMany: !0,
        isVirtual: !0
      }
    ]
  },
  {
    name: "Label",
    isAbstract: !0,
    superClass: [
      "Node"
    ],
    properties: [
      {
        name: "bounds",
        type: "dc:Bounds"
      }
    ]
  },
  {
    name: "Style",
    isAbstract: !0,
    properties: [
      {
        name: "id",
        isAttr: !0,
        isId: !0,
        type: "String"
      }
    ]
  },
  {
    name: "Extension",
    properties: [
      {
        name: "values",
        isMany: !0,
        type: "Element"
      }
    ]
  }
], Dp = [], jp = {
  tagAlias: "lowerCase"
}, Bp = {
  name: Ap,
  uri: Mp,
  prefix: Op,
  types: Pp,
  associations: Dp,
  xml: jp
}, zp = "bpmn.io colors for BPMN", Lp = "http://bpmn.io/schema/bpmn/biocolor/1.0", Fp = "bioc", qp = [
  {
    name: "ColoredShape",
    extends: [
      "bpmndi:BPMNShape"
    ],
    properties: [
      {
        name: "stroke",
        isAttr: !0,
        type: "String"
      },
      {
        name: "fill",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "ColoredEdge",
    extends: [
      "bpmndi:BPMNEdge"
    ],
    properties: [
      {
        name: "stroke",
        isAttr: !0,
        type: "String"
      },
      {
        name: "fill",
        isAttr: !0,
        type: "String"
      }
    ]
  }
], $p = [], Up = [], Gp = {
  name: zp,
  uri: Lp,
  prefix: Fp,
  types: qp,
  enumerations: $p,
  associations: Up
}, Hp = "BPMN in Color", Vp = "http://www.omg.org/spec/BPMN/non-normative/color/1.0", Kp = "color", Wp = [
  {
    name: "ColoredLabel",
    extends: [
      "bpmndi:BPMNLabel"
    ],
    properties: [
      {
        name: "color",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "ColoredShape",
    extends: [
      "bpmndi:BPMNShape"
    ],
    properties: [
      {
        name: "background-color",
        isAttr: !0,
        type: "String"
      },
      {
        name: "border-color",
        isAttr: !0,
        type: "String"
      }
    ]
  },
  {
    name: "ColoredEdge",
    extends: [
      "bpmndi:BPMNEdge"
    ],
    properties: [
      {
        name: "border-color",
        isAttr: !0,
        type: "String"
      }
    ]
  }
], Xp = [], Qp = [], Jp = {
  name: Hp,
  uri: Vp,
  prefix: Kp,
  types: Wp,
  enumerations: Xp,
  associations: Qp
};
const Yp = {
  bpmn: yp,
  bpmndi: _p,
  dc: Np,
  di: Bp,
  bioc: Gp,
  color: Jp
};
function Zp(e, t) {
  const n = Y({}, Yp, e);
  return new pn(n, t);
}
function em(e) {
  return !!e.sourceRef;
}
function On(e) {
  return !!e.attachedToRef;
}
function fi(e, t, n = /* @__PURE__ */ new Set()) {
  if (e === t) return !0;
  if (n.has(e) || (n.add(e), !e.outgoing || e.outgoing.length === 0)) return !1;
  for (let r of e.outgoing.map((a) => a.targetRef))
    if (fi(r, t, n))
      return !0;
  return !1;
}
const Dt = 80, Xt = 100;
function gi(e) {
  return ce(e, "bpmn:SubProcess") ? { width: Xt, height: Dt } : ce(e, "bpmn:Task") ? { width: Xt, height: Dt } : ce(e, "bpmn:Gateway") ? { width: 50, height: 50 } : ce(e, "bpmn:Event") ? { width: 36, height: 36 } : ce(e, "bpmn:Participant") ? { width: 400, height: 100 } : ce(e, "bpmn:Lane") ? { width: 400, height: 100 } : ce(e, "bpmn:DataObjectReference") ? { width: 36, height: 50 } : ce(e, "bpmn:DataStoreReference") ? { width: 50, height: 50 } : ce(e, "bpmn:TextAnnotation") ? { width: Xt, height: 30 } : { width: Xt, height: Dt };
}
function ce(e, t) {
  return e.$instanceOf(t);
}
const re = 150, L = 140;
function Pn(e) {
  return {
    x: e.x + e.width / 2,
    y: e.y + e.height / 2
  };
}
function ee(e, t, n = "r", r = "top-left") {
  if (n === "h" && (n = /left/.test(r) ? "l" : "r"), n === "v" && (n = /top/.test(r) ? "t" : "b"), n === "t")
    return { original: e, x: e.x, y: t.y };
  if (n === "r")
    return { original: e, x: t.x + t.width, y: e.y };
  if (n === "b")
    return { original: e, x: e.x, y: t.y + t.height };
  if (n === "l")
    return { original: e, x: t.x, y: e.y };
  throw new Error("unexpected dockingDirection: <" + n + ">");
}
function yi(e, t, n) {
  const r = e.di, a = t.di, i = r.get("bounds"), s = a.get("bounds"), o = Pn(i), l = Pn(s), c = t.gridPosition.col - e.gridPosition.col, u = t.gridPosition.row - e.gridPosition.row, d = `${u > 0 ? "bottom" : "top"}-${c > 0 ? "right" : "left"}`, p = `${u > 0 ? "top" : "bottom"}-${c > 0 ? "left" : "right"}`, m = e.grid || e.attachedToRef?.grid, h = t.grid;
  if (c === 0 && u === 0) {
    const { x: k, y: f } = gn(e.gridPosition.row, e.gridPosition.col);
    return [
      ee(o, i, "r", d),
      { x: m ? k + (m.getGridDimensions()[1] + 1) * re : k + re, y: o.y },
      { x: m ? k + (m.getGridDimensions()[1] + 1) * re : k + re, y: f },
      { x: l.x, y: f },
      ee(l, s, "t", p)
    ];
  }
  if (c < 0) {
    const { y: k } = gn(e.gridPosition.row, e.gridPosition.col), f = L / 2;
    if (o.y >= l.y) {
      const w = nm(e, t, n);
      return w ? [
        ee(o, i, "b"),
        { x: o.x, y: m ? k + (m.getGridDimensions()[0] + 1) * L : k + L + w * L },
        { x: l.x, y: m ? k + (m.getGridDimensions()[0] + 1) * L : k + L + w * L },
        ee(l, s, "b")
      ] : [
        ee(o, i, "b"),
        { x: o.x, y: m ? k + (m.getGridDimensions()[0] + 1) * L : k + L },
        { x: l.x, y: m ? k + (m.getGridDimensions()[0] + 1) * L : k + L },
        ee(l, s, "b")
      ];
    } else {
      const w = o.y - f;
      return [
        ee(o, i, "t"),
        { x: o.x, y: w },
        { x: l.x, y: w },
        ee(l, s, "t")
      ];
    }
  }
  if (u === 0)
    if (ir(e, t, n)) {
      const { y: k } = gn(e.gridPosition.row, e.gridPosition.col);
      return [
        ee(o, i, "b"),
        { x: o.x, y: m ? k + (m.getGridDimensions()[0] + 1) * L : k + L },
        { x: l.x, y: m ? k + (m.getGridDimensions()[0] + 1) * L : k + L },
        ee(l, s, "b")
      ];
    } else {
      const k = ee(o, i, "h", d), f = ee(l, s, "h", p);
      return m && (k.y = i.y + Dt / 2), h && (f.y = s.y + Dt / 2), [
        k,
        f
      ];
    }
  if (c === 0)
    if (ir(e, t, n)) {
      const k = -Math.sign(u) * L / 2;
      return [
        ee(o, i, "r"),
        { x: o.x + re / 2, y: o.y },
        // out right
        { x: l.x + re / 2, y: l.y + k },
        { x: l.x, y: l.y + k },
        ee(l, s, Math.sign(k) > 0 ? "b" : "t")
      ];
    } else
      return [
        ee(o, i, "v", d),
        ee(l, s, "v", p)
      ];
  const g = tm(e, t, n);
  if (g) {
    const k = ee(o, i, g[0], d), f = ee(l, s, g[1], p), w = g[0] === "h" ? { x: f.x, y: k.y } : { x: k.x, y: f.y };
    return [
      k,
      w,
      f
    ];
  }
  const v = -Math.sign(u) * L / 2;
  return [
    ee(o, i, "r", d),
    { x: o.x + re / 2, y: o.y },
    // out right
    { x: o.x + re / 2, y: l.y + v },
    // to target row
    { x: l.x - re / 2, y: l.y + v },
    // to target column
    { x: l.x - re / 2, y: l.y },
    // to mid
    ee(l, s, "l", p)
  ];
}
function gn(e, t) {
  return {
    width: re,
    height: L,
    x: t * re,
    y: e * L
  };
}
function Dn(e, t, n, r, a) {
  let { width: i, height: s } = gi(e);
  const { x: o, y: l } = r;
  if (!a)
    return {
      width: e.isExpanded ? e.grid.getGridDimensions()[1] * re + i : i,
      height: e.isExpanded ? e.grid.getGridDimensions()[0] * L + s : s,
      x: n * re + (re - i) / 2 + o,
      y: t * L + (L - s) / 2 + l
    };
  const c = a.di.bounds;
  return {
    width: i,
    height: s,
    x: Math.round(c.x + c.width / 2 - i / 2),
    y: Math.round(c.y + c.height - s / 2)
  };
}
function ir(e, t, n) {
  const { row: r, col: a } = e.gridPosition, { row: i, col: s } = t.gridPosition, o = s - a, l = i - r;
  let c = 0;
  return o && (c += n.getElementsInRange({ row: r, col: a }, { row: r, col: s }).length), l && (c += n.getElementsInRange({ row: r, col: s }, { row: i, col: s }).length), c > 2;
}
function tm(e, t, n) {
  const { row: r, col: a } = e.gridPosition, { row: i, col: s } = t.gridPosition, o = s - a, l = i - r;
  if (o > 0 && l !== 0)
    if (l > 0) {
      let c = 0;
      const u = { row: i, col: a };
      return c += n.getElementsInRange({ row: r, col: a }, u).length, c += n.getElementsInRange(u, { row: i, col: s }).length, c > 2 ? !1 : ["v", "h"];
    } else {
      let c = 0;
      const u = { row: r, col: s };
      return c += n.getElementsInRange({ row: r, col: a }, u).length, c += n.getElementsInRange(u, { row: i, col: s }).length, c > 2 ? !1 : ["h", "v"];
    }
}
function nm(e, t, n) {
  const r = e.attachedToRef ? e.attachedToRef : e, a = t.attachedToRef ? t.attachedToRef : t, [i, s] = n.find(r), [, o] = n.find(a), l = s < o ? s : o, c = s < o ? o : s;
  return n.getAllElements().filter((d) => d.gridPosition.row === i && d.gridPosition.col > l && d.gridPosition.col < c).reduce((d, p) => {
    if (p.grid?.getGridDimensions()[0] > d) return p.grid?.getGridDimensions()[0];
  }, 0);
}
class am {
  constructor() {
    this.grid = [];
  }
  add(t, n) {
    if (!n) {
      this._addStart(t);
      return;
    }
    const [r, a] = n;
    if (!r && !a && this._addStart(t), this.grid[r] || (this.grid[r] = []), this.grid[r][a])
      throw new Error("Grid is occupied please ensure the place you insert at is not occupied");
    this.grid[r][a] = t;
  }
  createRow(t) {
    !t && !Number.isInteger(t) ? this.grid.push([]) : this.grid.splice(t + 1, 0, []);
  }
  _addStart(t) {
    this.grid.push([t]);
  }
  addAfter(t, n) {
    t || this._addStart(n);
    const [r, a] = this.find(t);
    this.grid[r].splice(a + 1, 0, n);
  }
  addBelow(t, n) {
    t || this._addStart(n);
    const [r, a] = this.find(t);
    if (this.grid[r + 1] || (this.grid[r + 1] = []), this.grid[r + 1][a] && this.grid.splice(r + 1, 0, []), this.grid[r + 1][a])
      throw new Error("Grid is occupied and we could not find a place - this should not happen");
    this.grid[r + 1][a] = n;
  }
  find(t) {
    let n, r;
    return n = this.grid.findIndex((a) => (r = a.findIndex((i) => i === t), r !== -1)), [n, r];
  }
  get(t, n) {
    return (this.grid[t] || [])[n];
  }
  getElementsInRange({ row: t, col: n }, { row: r, col: a }) {
    const i = [];
    t > r && ([t, r] = [r, t]), n > a && ([n, a] = [a, n]);
    for (let s = t; s <= r; s++)
      for (let o = n; o <= a; o++) {
        const l = this.get(s, o);
        l && i.push(l);
      }
    return i;
  }
  adjustGridPosition(t) {
    let [n, r] = this.find(t);
    const [, a] = this.getGridDimensions();
    r < a - 1 && (this.grid[n].length = a, this.grid[n][a] = t, this.grid[n][r] = null);
  }
  adjustRowForMultipleIncoming(t, n) {
    const r = t.map((o) => this.find(o)), a = Math.min(...r.map((o) => o[0]).filter((o) => o >= 0)), [i, s] = this.find(n);
    a < i && !this.grid[a][s] && (this.grid[a][s] = n, this.grid[i][s] = null);
  }
  adjustColumnForMultipleIncoming(t, n) {
    const r = t.map((o) => this.find(o)), a = Math.max(...r.map((o) => o[1]).filter((o) => o >= 0)), [i, s] = this.find(n);
    a + 1 > s && (this.grid[i][a + 1] = n, this.grid[i][s] = null);
  }
  getAllElements() {
    const t = [];
    for (let n = 0; n < this.grid.length; n++)
      for (let r = 0; r < this.grid[n].length; r++) {
        const a = this.get(n, r);
        a && t.push(a);
      }
    return t;
  }
  getGridDimensions() {
    const t = this.grid.length;
    let n = 0;
    for (let r = 0; r < t; r++) {
      const a = this.grid[r].length;
      a > n && (n = a);
    }
    return [t, n];
  }
  elementsByPosition() {
    const t = [];
    return this.grid.forEach((n, r) => {
      n.forEach((a, i) => {
        a && t.push({
          element: a,
          row: r,
          col: i
        });
      });
    }), t;
  }
  getElementsTotal() {
    const t = this.grid.flat();
    return new Set(t.filter((r) => r)).size;
  }
  /**
   *
   * @param {number} afterIndex - number is integer
   * @param {number=} colCount - number is positive integer
   */
  createCol(t, n) {
    this.grid.forEach((r, a) => {
      this.expandRow(a, t, n);
    });
  }
  /**
   * @param {number} rowIndex - is positive integer
   * @param {number} afterIndex - is integer
   * @param {number=} colCount - is positive integer
   */
  expandRow(t, n, r) {
    if (!Number.isInteger(t) || t < 0 || t > this.rowCount - 1) return;
    const a = Number.isInteger(r) && r > 0 ? Array(r) : Array(1), i = this.grid[t];
    !n && !Number.isInteger(n) ? i.splice(i.length, 0, ...a) : i.splice(n + 1, 0, ...a);
  }
}
class rm {
  constructor(t) {
    this.moddle = t;
  }
  create(t, n) {
    return this.moddle.create(t, n || {});
  }
  createDiBounds(t) {
    return this.create("dc:Bounds", t);
  }
  createDiLabel() {
    return this.create("bpmndi:BPMNLabel", {
      bounds: this.createDiBounds()
    });
  }
  createDiShape(t, n, r) {
    return this.create("bpmndi:BPMNShape", Y({
      bpmnElement: t,
      bounds: this.createDiBounds(n)
    }, r));
  }
  createDiWaypoints(t) {
    var n = this;
    return Sd(t, function(r) {
      return n.createDiWaypoint(r);
    });
  }
  createDiWaypoint(t) {
    return this.create("dc:Point", ti(t, ["x", "y"]));
  }
  createDiEdge(t, n, r) {
    return this.create("bpmndi:BPMNEdge", Y({
      bpmnElement: t,
      waypoint: this.createDiWaypoints(n)
    }, r));
  }
  createDiPlane(t) {
    return this.create("bpmndi:BPMNPlane", t);
  }
  createDiDiagram(t) {
    return this.create("bpmndi:BPMNDiagram", t);
  }
}
var im = {
  addToGrid: ({ element: e, grid: t, visited: n }) => {
    const r = [];
    return (e.attachers || []).map((i) => (i.outgoing || []).reverse()).flat().map((i) => i.targetRef).forEach((i, s, o) => {
      n.has(i) || (sm(i, e, t), r.push(i), n.add(i));
    }), r;
  },
  createElementDi: ({ element: e, row: t, col: n, diFactory: r, shift: a }) => {
    const i = Dn(e, t, n, a), s = [];
    return (e.attachers || []).forEach((o, l, c) => {
      o.gridPosition = { row: t, col: n };
      const u = Dn(o, t, n, a, e);
      u.x = i.x + (l + 1) * (i.width / (c.length + 1)) - u.width / 2;
      const d = r.createDiShape(o, u, {
        id: o.id + "_di"
      });
      o.di = d, o.gridPosition = { row: t, col: n }, s.push(d);
    }), s;
  },
  createConnectionDi: ({ element: e, row: t, col: n, layoutGrid: r, diFactory: a, shift: i }) => (e.attachers || []).flatMap((o) => (o.outgoing || []).map((c) => {
    const u = c.targetRef, d = yi(o, u, r);
    return om(o, d, [t, n]), a.createDiEdge(c, d, {
      id: c.id + "_di"
    });
  }))
};
function sm(e, t, n) {
  const [r, a] = n.find(t);
  (n.get(r + 1, a) || n.get(r + 1, a + 1)) && n.createRow(r), n.add(e, [r + 1, a + 1]);
}
function om(e, t, [n, r]) {
  const i = e.di.get("bounds"), s = Pn(i), o = ee(s, i, "b");
  if (t[0].x === o.x && t[0].y === o.y)
    return;
  const l = e.grid || e.attachedToRef?.grid;
  if (t.length === 2) {
    const u = [
      o,
      { x: o.x, y: l ? (n + l.getGridDimensions()[0] + 1) * L : (n + 1) * L },
      { x: l ? (r + l.getGridDimensions()[1] + 1) * re : (r + 1) * re, y: l ? (n + l.getGridDimensions()[0] + 1) * L : (n + 1) * L },
      { x: l ? (r + l.getGridDimensions()[1] + 1) * re : (r + 1) * re, y: l ? n * L + L / 2 : (n + 0.5) * L }
    ];
    t.splice(0, 1, ...u);
    return;
  }
  const c = [
    o,
    { x: o.x, y: l ? (n + l.getGridDimensions()[0] + 1) * L : (n + 1) * L },
    { x: t[1].x, y: l ? (n + l.getGridDimensions()[0] + 1) * L : (n + 1) * L }
  ];
  t.splice(0, 1, ...c);
}
var lm = {
  createElementDi: ({ element: e, row: t, col: n, diFactory: r, shift: a }) => {
    const i = Dn(e, t, n, a), s = {
      id: e.id + "_di"
    };
    ce(e, "bpmn:ExclusiveGateway") && (s.isMarkerVisible = !0), e.isExpanded && (s.isExpanded = !0);
    const o = r.createDiShape(e, i, s);
    return e.di = o, e.gridPosition = { row: t, col: n }, o;
  }
}, cm = {
  addToGrid: ({ element: e, grid: t, visited: n, stack: r }) => {
    let a = [];
    const i = (e.outgoing || []).map((o) => o.targetRef).filter((o) => o);
    let s = null;
    return i.length > 1 && mm(i) && t.adjustGridPosition(e), i.forEach((o, l, c) => {
      n.has(o) || (s || r.length > 0) && pm(o, n) && !dm(o, n) || (s ? ce(e, "bpmn:ExclusiveGateway") && ce(o, "bpmn:ExclusiveGateway") ? t.addAfter(s, o) : t.addBelow(c[l - 1], o) : t.addAfter(e, o), o !== e && (s = o), a.unshift(o), n.add(o));
    }), a = um(a, "bpmn:ExclusiveGateway"), a;
  },
  createConnectionDi: ({ element: e, row: t, col: n, layoutGrid: r, diFactory: a }) => (e.outgoing || []).map((s) => {
    const o = s.targetRef, l = yi(e, o, r);
    return a.createDiEdge(s, l, {
      id: s.id + "_di"
    });
  })
};
function um(e, t) {
  const n = e.filter((a) => !ce(a, t));
  return [...e.filter((a) => ce(a, t)), ...n];
}
function dm(e, t) {
  for (const n of e.incoming)
    if (!t.has(n.sourceRef))
      return fi(e, n.sourceRef);
}
function pm(e, t) {
  if (e.incoming.length > 1) {
    for (const n of e.incoming)
      if (!t.has(n.sourceRef))
        return !0;
  }
  return !1;
}
function mm(e) {
  return e.every((t) => ce(t, "bpmn:Task"));
}
var hm = {
  addToGrid: ({ element: e, grid: t, visited: n }) => {
    const r = [], a = (e.incoming || []).map((i) => i.sourceRef).filter((i) => i);
    return a.length > 1 && (t.adjustColumnForMultipleIncoming(a, e), t.adjustRowForMultipleIncoming(a, e)), r;
  }
};
const fm = [lm, hm, cm, im];
class gm {
  constructor() {
    this.moddle = new Zp(), this.diFactory = new rm(this.moddle), this._handlers = fm;
  }
  handle(t, n) {
    return this._handlers.filter((r) => Zr(r[t])).map((r) => r[t](n));
  }
  async layoutProcess(t) {
    const n = await this.moddle.fromXML(t), { rootElement: r } = n;
    this.diagram = r;
    const a = this.getProcess();
    return a && (this.setExpandedPropertyToModdleElements(n), this.setExecutedProcesses(a), this.createGridsForProcesses(), this.cleanDi(), this.createRootDi(a), this.drawProcesses()), (await this.moddle.toXML(this.diagram, { format: !0 })).xml;
  }
  createGridsForProcesses() {
    const t = this.layoutedProcesses.sort((n, r) => r.level - n.level);
    for (const n of t)
      if (n.grid = this.createGridLayout(n), bm(n.grid), vm(n.grid), n.isExpanded) {
        const [r, a] = n.grid.getGridDimensions();
        r === 0 && n.grid.createRow(), a === 0 && n.grid.createCol();
      }
  }
  setExpandedPropertyToModdleElements(t) {
    const n = t.elementsById;
    if (n)
      for (const r of Object.values(n))
        r.$type === "bpmndi:BPMNShape" && r.isExpanded === !0 && (r.bpmnElement.isExpanded = !0);
  }
  setExecutedProcesses(t) {
    this.layoutedProcesses = [];
    const n = [t];
    for (; n.length > 0; ) {
      const r = n.pop();
      this.layoutedProcesses.push(r), r.level = r.$parent === this.diagram ? 0 : r.$parent.level + 1;
      const a = r.get("flowElements").filter((i) => ce(i, "bpmn:SubProcess"));
      n.splice(n.length, 0, ...a);
    }
  }
  cleanDi() {
    this.diagram.diagrams = [];
  }
  createGridLayout(t) {
    const n = new am(), r = t.flowElements || [], a = r.filter((s) => !ce(s, "bpmn:SequenceFlow"));
    if (!r)
      return n;
    ym(r);
    const i = /* @__PURE__ */ new Set();
    for (; i.size < a.filter((s) => !s.attachedToRef).length; ) {
      const s = r.filter((l) => !em(l) && !On(l) && (!l.incoming || !km(l)) && !i.has(l)), o = [...s];
      if (s.forEach((l) => {
        n.add(l), i.add(l);
      }), this.handleGrid(n, i, o), n.getElementsTotal() !== a.length) {
        const l = n.getAllElements(), c = a.filter((u) => !l.includes(u) && !On(u));
        c.length > 0 && (o.push(c[0]), n.add(c[0]), i.add(c[0]), this.handleGrid(n, i, o));
      }
    }
    return n;
  }
  generateDi(t, n, r) {
    const a = this.diFactory, s = (r || this.diagram.diagrams[0]).plane.get("planeElement");
    t.elementsByPosition().forEach(({ element: o, row: l, col: c }) => {
      const u = this.handle("createElementDi", { element: o, row: l, col: c, layoutGrid: t, diFactory: a, shift: n }).flat();
      s.push(...u);
    }), t.elementsByPosition().forEach(({ element: o, row: l, col: c }) => {
      const u = this.handle("createConnectionDi", { element: o, row: l, col: c, layoutGrid: t, diFactory: a, shift: n }).flat();
      s.push(...u);
    });
  }
  handleGrid(t, n, r) {
    for (; r.length > 0; ) {
      const a = r.pop();
      this.handle("addToGrid", { element: a, grid: t, visited: n, stack: r }).flat().forEach((s) => {
        r.push(s), n.add(s);
      });
    }
  }
  getProcess() {
    return this.diagram.get("rootElements").find((t) => t.$type === "bpmn:Process");
  }
  createRootDi(t) {
    this.createProcessDi(t);
  }
  createProcessDi(t) {
    const n = this.diFactory, r = n.createDiPlane({
      id: "BPMNPlane_" + t.id,
      bpmnElement: t
    }), a = n.createDiDiagram({
      id: "BPMNDiagram_" + t.id,
      plane: r
    });
    return this.diagram.diagrams.push(a), a;
  }
  /**
   * Draw processes.
   * Root processes should be processed first for element expanding
   */
  drawProcesses() {
    const t = this.layoutedProcesses.sort((n, r) => n.level - r.level);
    for (const n of t) {
      if (n.isExpanded) {
        const a = this.getElementDi(n), i = this.getProcDi(a);
        let { x: s, y: o } = a.bounds;
        const { width: l, height: c } = gi(n);
        s += re / 2 - l / 4, o += L - c - c / 4, this.generateDi(n.grid, { x: s, y: o }, i);
        continue;
      }
      const r = this.diagram.diagrams.find((a) => a.plane.bpmnElement === n);
      this.generateDi(n.grid, { x: 0, y: 0 }, r);
    }
  }
  getElementDi(t) {
    return this.diagram.diagrams.map((n) => n.plane.planeElement).flat().find((n) => n.bpmnElement === t);
  }
  getProcDi(t) {
    return this.diagram.diagrams.find((n) => n.plane.planeElement.includes(t));
  }
}
function ym(e) {
  e.filter((n) => On(n)).forEach((n) => {
    const r = n.attachedToRef, a = r.attachers || [];
    a.push(n), r.attachers = a;
  });
}
function bm(e) {
  const [t, n] = e.getGridDimensions();
  for (let r = n - 1; r >= 0; r--) {
    const a = [];
    for (let o = 0; o < t; o++) {
      const l = e.get(o, r);
      l && l.isExpanded && a.push(l);
    }
    if (a.length === 0) continue;
    const i = a.reduce((o, l) => {
      const [, c] = l.grid.getGridDimensions();
      return o === void 0 || c > o ? c : o;
    }, void 0), s = i || 2;
    e.createCol(r, s);
  }
}
function vm(e) {
  const [t, n] = e.getGridDimensions();
  for (let r = t - 1; r >= 0; r--) {
    const a = [];
    for (let o = 0; o < n; o++) {
      const l = e.get(r, o);
      l && l.isExpanded && a.push(l);
    }
    if (a.length === 0) continue;
    const i = a.reduce((o, l) => {
      const [c] = l.grid.getGridDimensions();
      return o === void 0 || c > o ? c : o;
    }, void 0), s = i || 1;
    for (let o = 0; o < s; o++)
      e.createRow(r);
  }
}
function km(e) {
  const t = e.incoming?.filter((r) => r.sourceRef !== e && r.sourceRef.attachedToRef === void 0) || [], n = e.incoming?.filter((r) => r.sourceRef !== e && r.sourceRef.attachedToRef !== e);
  return t?.length > 0 || n?.length > 0;
}
function bi(e) {
  return new gm().layoutProcess(e);
}
const wm = (e) => ({
  name: "get_selected_element",
  description: "Gets the currently selected BPMN element on the canvas. Returns the element ID, type, and name, or null if nothing is selected. Use this to understand which element the user is referring to.",
  parameters: {},
  type: "frontend",
  contentType: "TEXT",
  category: "BPMN_QUERY",
  requiredCapabilities: ["BPMN_VIEWING"],
  descriptionFormatter: () => "Reading selection",
  handler: async (t, n) => {
    try {
      const r = e.modeler.getSelection().get();
      if (!r || r.length === 0)
        return { selected: null };
      const a = r[0];
      return {
        selected: {
          id: a.id,
          type: a.type,
          name: a.businessObject?.name ?? ""
        }
      };
    } catch (r) {
      throw n("get_selected_element", r), r;
    }
  }
}), xm = (e) => e.includes(":") ? e : `bpmn:${e[0].toUpperCase()}${e.slice(1)}`, Em = (e) => ({
  name: "find_elements_by_type",
  description: "Finds all BPMN elements of a specific type in the current diagram. Returns a compact list with id, type, and name for each match. Supported types: userTask, serviceTask, startEvent, endEvent, exclusiveGateway, parallelGateway, subProcess, sequenceFlow, etc.",
  parameters: {
    type: "object",
    properties: {
      bpmnType: {
        type: "string",
        description: "The BPMN element type to search for (e.g., 'userTask', 'serviceTask', 'bpmn:UserTask', 'bpmn:SequenceFlow')"
      }
    },
    required: ["bpmnType"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "BPMN_QUERY",
  requiredCapabilities: ["BPMN_VIEWING"],
  descriptionFormatter: () => "Searching elements",
  handler: async (t, n) => {
    try {
      const r = xm(t.bpmnType);
      return e.modeler.getElementRegistry().filter((a) => e.modeler.is(a, r)).map((a) => ({
        id: a.id,
        type: a.type,
        name: a.businessObject?.name ?? ""
      }));
    } catch (r) {
      throw n("find_elements_by_type", r), r;
    }
  }
}), _m = (e, t) => {
  const n = t(e);
  return {
    id: e.id,
    type: e.type,
    name: n.name ?? "",
    incoming: (e.incoming ?? []).map((r) => r.id),
    outgoing: (e.outgoing ?? []).map((r) => r.id)
  };
}, Im = (e) => ({
  name: "get_process_summary",
  description: "Gets a comprehensive summary of the current BPMN process including process ID, name, whether it is executable, counts of flow nodes/tasks/sequence flows, and a list of all flow nodes with their ID, type, name, and connections. Use this to understand the overall structure of the process.",
  parameters: {},
  type: "frontend",
  contentType: "TEXT",
  category: "BPMN_QUERY",
  requiredCapabilities: ["BPMN_VIEWING"],
  descriptionFormatter: () => "Analyzing process structure",
  handler: async (t, n) => {
    try {
      const { is: r, getBusinessObject: a, getElementRegistry: i } = e.modeler, s = i(), o = s.filter((p) => r(p, "bpmn:Process"))[0], l = o ? a(o) : null, c = s.filter((p) => r(p, "bpmn:FlowNode")), u = s.filter((p) => r(p, "bpmn:Task")), d = s.filter((p) => r(p, "bpmn:SequenceFlow"));
      return {
        processId: l?.id ?? "",
        name: l?.name ?? "",
        isExecutable: l?.isExecutable ?? !1,
        flowNodeCount: c.length,
        taskCount: u.length,
        flowCount: d.length,
        flowNodes: c.map((p) => _m(p, a))
      };
    } catch (r) {
      throw n("get_process_summary", r), r;
    }
  }
}), sr = (e, t) => {
  const n = t(e).conditionExpression;
  return {
    flowId: e.id,
    condition: n ? n.body ?? n.text ?? "" : null
  };
}, Cm = (e) => ({
  name: "get_element_connections",
  description: "Gets all incoming and outgoing sequence flow connections for a specific BPMN element. Returns flow IDs, connected element IDs/names, and condition expressions if present.",
  parameters: {
    type: "object",
    properties: {
      elementId: {
        type: "string",
        description: "The ID of the BPMN element to get connections for"
      }
    },
    required: ["elementId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "BPMN_QUERY",
  requiredCapabilities: ["BPMN_VIEWING"],
  descriptionFormatter: () => "Checking connections",
  handler: async (t, n) => {
    try {
      const { is: r, getBusinessObject: a, getElementById: i } = e.modeler, s = i(t.elementId), o = (s.incoming ?? []).filter((c) => r(c, "bpmn:SequenceFlow")).map((c) => ({
        ...sr(c, a),
        sourceId: c.source?.id ?? null,
        sourceName: c.source?.businessObject?.name ?? ""
      })), l = (s.outgoing ?? []).filter((c) => r(c, "bpmn:SequenceFlow")).map((c) => ({
        ...sr(c, a),
        targetId: c.target?.id ?? null,
        targetName: c.target?.businessObject?.name ?? ""
      }));
      return { incoming: o, outgoing: l };
    } catch (r) {
      throw n("get_element_connections", r), r;
    }
  }
}), rn = (e, t) => typeof e.get == "function" ? e.get(t) : e[t], ot = (e, t, n) => {
  const r = rn(e, "extensionElements");
  return r ? rn(r, "values")?.find((a) => n(a, t)) ?? null : null;
}, Ft = (e, t, n, r, a, i) => {
  const s = r.get("modeling"), o = r.get("moddle"), l = a(e), c = rn(l, "extensionElements");
  if (!c) {
    const d = o.create("bpmn:ExtensionElements"), p = o.create(t, n);
    p.$parent = d, d.values = [p], s.updateProperties(e, { extensionElements: d });
    return;
  }
  const u = ot(l, t, i);
  if (!u) {
    const d = o.create(t, n);
    d.$parent = c;
    const p = rn(c, "values") ?? [];
    s.updateModdleProperties(e, c, {
      values: [...p, d]
    });
    return;
  }
  s.updateModdleProperties(e, u, n);
}, Tm = (e, t) => {
  const n = ot(e, "zeebe:IoMapping", t);
  if (!n)
    return { inputs: [], outputs: [] };
  const r = (a) => {
    const i = a;
    return { source: i.source, target: i.target };
  };
  return {
    inputs: (n.inputParameters ?? []).map(r),
    outputs: (n.outputParameters ?? []).map(r)
  };
}, Sm = (e, t) => {
  const n = ot(e, "zeebe:TaskDefinition", t);
  return n ? {
    type: n.type ?? "",
    retries: n.retries ?? ""
  } : null;
}, Rm = (e, t) => {
  const n = ot(e, "zeebe:FormDefinition", t);
  return n ? {
    formId: n.formId ?? null,
    formKey: n.formKey ?? null,
    bindingType: n.bindingType ?? "latest"
  } : null;
}, Nm = (e, t) => {
  const n = ot(e, "zeebe:Properties", t);
  return n ? (n.properties ?? []).reduce((r, a) => {
    const i = a;
    return { ...r, [i.name]: i.value };
  }, {}) : {};
}, Am = (e) => e.documentation?.[0]?.text ?? "", Mm = (e) => ({
  name: "get_element_details",
  description: "Gets detailed information about a specific BPMN element including ID, type, name, description, I/O mappings, Zeebe properties, task definition, and form binding. Use this to inspect a specific element in the process.",
  parameters: {
    type: "object",
    properties: {
      elementId: {
        type: "string",
        description: "The ID of the BPMN element to inspect"
      }
    },
    required: ["elementId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "BPMN_QUERY",
  requiredCapabilities: ["BPMN_VIEWING"],
  descriptionFormatter: () => "Inspecting element",
  handler: async (t, n) => {
    try {
      const { is: r, getBusinessObject: a, getElementById: i } = e.modeler, s = i(t.elementId), o = a(s), l = o.conditionExpression;
      return {
        id: s.id,
        type: s.type,
        name: o.name ?? "",
        description: Am(o),
        ...r(s, "bpmn:Activity") ? {
          ioMappings: Tm(o, r),
          zeebeProperties: Nm(o, r),
          taskDefinition: Sm(o, r)
        } : {},
        ...r(s, "bpmn:UserTask") ? { formBinding: Rm(o, r) } : {},
        ...r(s, "bpmn:SequenceFlow") ? {
          sourceId: o.sourceRef?.id ?? null,
          targetId: o.targetRef?.id ?? null,
          conditionExpression: l ? l.body ?? l.text ?? "" : null
        } : {}
      };
    } catch (r) {
      throw n("get_element_details", r), r;
    }
  }
}), Om = (e, t, n, r) => {
  const a = /* @__PURE__ */ new Set(), i = [[e]];
  for (; i.length > 0; ) {
    const s = i.shift();
    if (!s)
      break;
    const o = s[s.length - 1];
    if (o === t)
      return s;
    if (a.has(o))
      continue;
    a.add(o);
    const l = n.get(o);
    l && (l.outgoing ?? []).filter((c) => r(c, "bpmn:SequenceFlow")).map((c) => c.target?.id).filter((c) => c !== void 0 && !a.has(c)).forEach((c) => i.push([...s, c]));
  }
  return [];
}, Pm = (e) => ({
  name: "get_path_between_elements",
  description: "Finds the shortest path between two BPMN elements following sequence flows. Returns an ordered list of elements on the path with their IDs, types, and names. Returns an empty path if no route exists.",
  parameters: {
    type: "object",
    properties: {
      sourceId: {
        type: "string",
        description: "The ID of the source element (start of path)"
      },
      targetId: {
        type: "string",
        description: "The ID of the target element (end of path)"
      }
    },
    required: ["sourceId", "targetId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "BPMN_QUERY",
  requiredCapabilities: ["BPMN_VIEWING"],
  descriptionFormatter: () => "Finding path",
  handler: async (t, n) => {
    try {
      const { is: r, getElementById: a, getElementRegistry: i } = e.modeler;
      a(t.sourceId), a(t.targetId);
      const s = i(), o = Om(t.sourceId, t.targetId, s, r);
      return {
        path: o.map((l) => {
          const c = s.get(l);
          return {
            id: l,
            type: c?.type ?? "unknown",
            name: c?.businessObject?.name ?? ""
          };
        }),
        length: o.length
      };
    } catch (r) {
      throw n("get_path_between_elements", r), r;
    }
  }
}), jn = {
  task: "bpmn:Task",
  userTask: "bpmn:UserTask",
  serviceTask: "bpmn:ServiceTask",
  sendTask: "bpmn:SendTask",
  receiveTask: "bpmn:ReceiveTask",
  businessRuleTask: "bpmn:BusinessRuleTask",
  manualTask: "bpmn:ManualTask",
  scriptTask: "bpmn:ScriptTask",
  startEvent: "bpmn:StartEvent",
  endEvent: "bpmn:EndEvent",
  intermediateThrowEvent: "bpmn:IntermediateThrowEvent",
  intermediateCatchEvent: "bpmn:IntermediateCatchEvent",
  boundaryEvent: "bpmn:BoundaryEvent",
  exclusiveGateway: "bpmn:ExclusiveGateway",
  inclusiveGateway: "bpmn:InclusiveGateway",
  parallelGateway: "bpmn:ParallelGateway",
  eventBasedGateway: "bpmn:EventBasedGateway",
  subProcess: "bpmn:SubProcess",
  adHocSubProcess: "bpmn:AdHocSubProcess",
  callActivity: "bpmn:CallActivity",
  textAnnotation: "bpmn:TextAnnotation",
  dataObjectReference: "bpmn:DataObjectReference",
  dataStoreReference: "bpmn:DataStoreReference",
  participant: "bpmn:Participant"
}, It = {
  message: "bpmn:MessageEventDefinition",
  timer: "bpmn:TimerEventDefinition",
  signal: "bpmn:SignalEventDefinition",
  error: "bpmn:ErrorEventDefinition",
  escalation: "bpmn:EscalationEventDefinition",
  compensation: "bpmn:CompensateEventDefinition",
  conditional: "bpmn:ConditionalEventDefinition",
  link: "bpmn:LinkEventDefinition",
  terminate: "bpmn:TerminateEventDefinition"
}, vi = (e) => {
  if (e.includes(":"))
    return e;
  const t = jn[e];
  if (!t)
    throw new Error(`Unsupported element type: ${e}. Supported types: ${Object.keys(jn).join(", ")}`);
  return t;
}, Dm = 100, jm = {
  task: "Generic task activity.",
  userTask: "Human interaction task.",
  serviceTask: "Automated service work.",
  sendTask: "Sends an outgoing message.",
  receiveTask: "Waits for an incoming message.",
  businessRuleTask: "Executes a business decision.",
  manualTask: "Represents manual offline work.",
  scriptTask: "Runs script logic.",
  startEvent: "Process start event.",
  endEvent: "Process end event.",
  intermediateThrowEvent: "Throws an intermediate event.",
  intermediateCatchEvent: "Catches an intermediate event.",
  boundaryEvent: "Event attached to an activity boundary.",
  exclusiveGateway: "Branches on one condition.",
  inclusiveGateway: "Branches on one or more conditions.",
  parallelGateway: "Runs paths in parallel.",
  eventBasedGateway: "Branches based on events.",
  subProcess: "Embedded subprocess container.",
  adHocSubProcess: "Ad-hoc subprocess for agentic orchestration.",
  callActivity: "Invokes a reusable process.",
  textAnnotation: "Documentation annotation.",
  dataObjectReference: "Process data object reference.",
  dataStoreReference: "Persistent data store reference.",
  participant: "Pool participant in collaboration."
}, Bm = {
  message: "Message event definition.",
  timer: "Timer event definition.",
  signal: "Signal event definition.",
  error: "Error event definition.",
  escalation: "Escalation event definition.",
  compensation: "Compensation event definition.",
  conditional: "Conditional event definition.",
  link: "Link event definition.",
  terminate: "Terminate event definition."
}, zm = (e) => {
  const t = [...e].sort((n, r) => n.id.localeCompare(r.id) || (r.version ?? 0) - (n.version ?? 0));
  return t.filter((n, r) => r === 0 || t[r - 1].id !== n.id);
}, Lm = (e) => ({
  name: "list_available_bpmn_elements",
  displayName: "Listing available BPMN elements",
  description: "Lists standard BPMN element types, event definitions, and available connector/element templates including custom organization templates.",
  parameters: {
    type: "object",
    properties: {}
  },
  type: "frontend",
  contentType: "TEXT",
  category: "BPMN_QUERY",
  requiredCapabilities: ["BPMN_VIEWING"],
  handler: async (t, n) => {
    try {
      const r = e.templates.getOotbConnectors(), a = e.templates.getCustom(), i = [
        ...r.map((l) => ({ ...l, _source: "ootb" })),
        ...a.map((l) => ({ ...l, _source: "custom" }))
      ], s = [...zm(i)].sort(
        (l, c) => (l.name || l.id || "").localeCompare(c.name || c.id || "")
      ), o = s.slice(0, Dm);
      return {
        standardElements: Object.entries(jn).map(([l, c]) => ({
          type: l,
          bpmnType: c,
          description: jm[l] ?? "Standard BPMN element."
        })),
        eventDefinitions: Object.entries(It).map(([l, c]) => ({
          type: l,
          bpmnType: c,
          description: Bm[l] ?? "Event definition."
        })),
        elementTemplates: o.map((l) => ({
          id: l.id,
          name: l.name,
          description: l.description,
          appliesTo: l.appliesTo,
          elementType: l.elementType,
          source: l._source,
          latestVersion: l.version
        })),
        totalTemplateCount: s.length
      };
    } catch (r) {
      throw n("list_available_bpmn_elements", r), new Error(`Failed to list available BPMN elements: ${r.message}`);
    }
  }
}), Fm = {
  userTask: "user task",
  serviceTask: "service task",
  sendTask: "send task",
  receiveTask: "receive task",
  businessRuleTask: "business rule task",
  scriptTask: "script task",
  manualTask: "manual task",
  task: "task",
  startEvent: "start event",
  endEvent: "end event",
  intermediateThrowEvent: "intermediate event",
  intermediateCatchEvent: "intermediate event",
  boundaryEvent: "boundary event",
  exclusiveGateway: "decision gateway",
  parallelGateway: "parallel gateway",
  inclusiveGateway: "inclusive gateway",
  eventBasedGateway: "event gateway",
  subProcess: "sub-process",
  adHocSubProcess: "AI agent",
  callActivity: "call activity",
  textAnnotation: "annotation",
  dataObjectReference: "data object",
  dataStoreReference: "data store",
  participant: "pool"
}, qm = (e) => Fm[e] ?? e?.replace(/([A-Z])/g, " $1").toLowerCase().trim() ?? "element", Qe = (e) => typeof e == "string" ? e : "", Be = (e) => async (t) => {
  const n = e.modeler.getModeler(), r = await t(n);
  await e.diagram.saveContent();
  const a = await e.diagram.getLintErrors();
  return { ...r, validationErrors: a };
}, Jt = (e) => e, $m = (e, t) => {
  if (!e)
    return t.modeler.getCanvas().getRootElement();
  const n = t.modeler.getElementById(e);
  if (!(t.modeler.is(n, "bpmn:SubProcess") || t.modeler.is(n, "bpmn:AdHocSubProcess") || t.modeler.is(n, "bpmn:Process") || t.modeler.is(n, "bpmn:Participant")))
    throw new Error(`Parent element ${e} must be a SubProcess, AdHocSubProcess, Process, or Participant`);
  return n;
}, Um = (e, t, n) => {
  if (t) {
    const a = Jt(t);
    return { x: (a.x ?? 0) + (a.width ?? 100) + 100, y: a.y ?? 200 };
  }
  if (n) {
    const a = Jt(n);
    return { x: (a.x ?? 0) - 200, y: a.y ?? 200 };
  }
  const r = Jt(e);
  return { x: (r.x ?? 0) + 200, y: (r.y ?? 0) + 100 };
}, Gm = (e) => {
  const t = Jt(e);
  return {
    x: (t.x ?? 0) + (t.width ?? 100) / 2,
    y: (t.y ?? 0) + (t.height ?? 80)
  };
}, Hm = (e) => Object.fromEntries(Object.entries(e).filter(([, t]) => t !== void 0)), Vm = (e, t, n) => {
  const r = t === "bpmn:BoundaryEvent" && !!e.attachedToRef, a = t === "bpmn:SubProcess", i = a || t === "bpmn:AdHocSubProcess";
  return Hm({
    type: t,
    eventDefinitionType: It[e.eventDefinition],
    host: r ? n.modeler.getElementById(e.attachedToRef) : void 0,
    cancelActivity: r && e.cancelActivity !== void 0 ? e.cancelActivity : void 0,
    isExpanded: i || void 0,
    triggeredByEvent: a && !!e.triggeredByEvent || void 0
  });
}, Km = (e, t, n, r) => {
  if (!n || !r)
    return;
  const a = (n.outgoing ?? []).find(
    (i) => t.modeler.is(i, "bpmn:SequenceFlow") && i.target?.id === r.id
  );
  a && e.removeElements([a]);
}, Wm = (e, t, n, r) => {
  n && t.modeler.is(n, "bpmn:FlowNode") && e.connect(n, r);
}, Xm = (e, t, n, r) => {
  n && t.modeler.is(n, "bpmn:FlowNode") && e.connect(r, n);
}, Qm = (e) => {
  const t = Be(e);
  return {
    name: "add_bpmn_element",
    description: "Adds a new BPMN element to the diagram. Optionally connects it to existing elements using afterElementId (incoming flow) and/or beforeElementId (outgoing flow). IMPORTANT: To insert a step between two elements that are ALREADY connected by a sequence flow, use 'insert_bpmn_element_between' instead - it splits the existing flow atomically. When both afterElementId and beforeElementId are provided and a direct sequence flow already exists between them, the existing flow is automatically removed to prevent duplicates. Returns validation errors after applying the change. Supported types: userTask, serviceTask, sendTask, receiveTask, businessRuleTask, manualTask, scriptTask, startEvent, endEvent, intermediateThrowEvent, intermediateCatchEvent, boundaryEvent, exclusiveGateway, inclusiveGateway, parallelGateway, eventBasedGateway, subProcess, adHocSubProcess, callActivity, textAnnotation.",
    parameters: {
      type: "object",
      properties: {
        type: {
          type: "string",
          description: "The BPMN element type (e.g., 'userTask', 'serviceTask', 'exclusiveGateway', 'startEvent')"
        },
        label: { type: "string", description: "Optional display name for the element" },
        afterElementId: {
          type: "string",
          description: "Optional element ID to connect FROM (creates incoming sequence flow)"
        },
        beforeElementId: {
          type: "string",
          description: "Optional element ID to connect TO (creates outgoing sequence flow)"
        },
        parentElementId: {
          type: "string",
          description: "Optional parent element ID for adding inside a subProcess"
        },
        eventDefinition: {
          type: "string",
          description: "Optional event definition type for events: 'message', 'timer', 'signal', 'error', 'escalation', 'compensation', 'conditional', 'link', 'terminate'"
        },
        attachedToRef: {
          type: "string",
          description: "Required for boundaryEvent: the ID of the element this boundary event is attached to"
        },
        cancelActivity: {
          type: "boolean",
          description: "For boundaryEvent: whether it cancels the activity (default: true)"
        },
        triggeredByEvent: {
          type: "boolean",
          description: "For subProcess: whether it is an event sub-process"
        }
      },
      required: ["type"]
    },
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: (n) => {
      const r = Qe(n.label) || Qe(n.name);
      if (r)
        return `Adding ${r}`;
      const a = Qe(n.type);
      return a ? `Adding ${qm(a)}` : "Adding element";
    },
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = e.modeler.getModeling(), i = e.modeler.getElementFactory(), s = vi(n.type);
          if (s === "bpmn:BoundaryEvent" && !n.attachedToRef)
            throw new Error("attachedToRef is required when adding a boundaryEvent");
          const o = s === "bpmn:BoundaryEvent", l = o ? e.modeler.getElementById(n.attachedToRef) : null, c = o ? l : $m(n.parentElementId, e), u = o ? Gm(l) : Um(
            c,
            n.afterElementId ? e.modeler.getElementById(n.afterElementId) : null,
            n.beforeElementId ? e.modeler.getElementById(n.beforeElementId) : null
          ), d = i.createShape(Vm(n, s, e)), p = o ? a.createShape(d, u, c, { attach: !0 }) : a.createShape(d, u, c), m = n.afterElementId ? e.modeler.getElementById(n.afterElementId) : null, h = n.beforeElementId ? e.modeler.getElementById(n.beforeElementId) : null;
          return Km(a, e, m, h), Wm(a, e, m, p), n.label && a.updateProperties(p, { name: n.label }), Xm(a, e, h, p), {
            elementId: p.id,
            type: p.type,
            label: n.label ?? "",
            ...o ? { skipAutoLayout: !0 } : {}
          };
        });
      } catch (a) {
        throw r("add_bpmn_element", a), new Error(`Failed to add BPMN element: ${a.message}`);
      }
    }
  };
}, Jm = async (e, t, n) => {
  const { xml: r } = await e.saveXML({ format: !0 }), a = await n(r), { error: i } = await t.importContent(a);
  if (i)
    throw new Error(`Auto-layout failed: ${i instanceof Error ? i.message : String(i)}`);
  t.ensureExecutionPlatform();
}, Ym = (e, t) => async (n) => {
  const r = e.modeler.getModeler(), a = await n(r);
  await Jm(r, e.diagram, t), await e.diagram.saveContent();
  const i = await e.diagram.getLintErrors();
  return { ...a, validationErrors: i };
}, Zm = (e, t) => {
  if (e.sequenceFlowId) {
    const i = t.modeler.getElementById(e.sequenceFlowId);
    if (!t.modeler.is(i, "bpmn:SequenceFlow"))
      throw new Error(`Element ${e.sequenceFlowId} is not a sequence flow`);
    return i;
  }
  if (!e.sourceElementId || !e.targetElementId)
    throw new Error("Provide either sequenceFlowId, or both sourceElementId and targetElementId");
  const n = t.modeler.getElementById(e.sourceElementId), r = t.modeler.getElementById(e.targetElementId), a = (n.outgoing ?? []).find(
    (i) => t.modeler.is(i, "bpmn:SequenceFlow") && i.target?.id === r.id
  );
  if (!a)
    throw new Error(
      `No sequence flow exists from ${e.sourceElementId} to ${e.targetElementId}. Use add_bpmn_element to create a new element with connections, or verify the element IDs.`
    );
  return a;
}, eh = (e) => {
  const t = e.waypoints ?? [];
  if (t.length === 0) {
    const a = e.source, i = e.target;
    if (!a || !i)
      throw new Error(`Sequence flow ${e.id} has no waypoints and no source/target`);
    return {
      x: ((a.x ?? 0) + (a.width ?? 0) / 2 + (i.x ?? 0) + (i.width ?? 0) / 2) / 2,
      y: ((a.y ?? 0) + (a.height ?? 0) / 2 + (i.y ?? 0) + (i.height ?? 0) / 2) / 2
    };
  }
  const n = t[0], r = t[t.length - 1];
  return {
    x: (n.x + r.x) / 2,
    y: (n.y + r.y) / 2
  };
}, th = (e, t) => ({
  type: t,
  ...e.eventDefinition && It[e.eventDefinition] ? { eventDefinitionType: It[e.eventDefinition] } : {}
}), nh = (e, t) => {
  const n = Ym(e, t);
  return {
    name: "insert_bpmn_element_between",
    description: "Inserts a new BPMN element on an existing sequence flow, splitting it in place. Prefer this tool over deleting + recreating flows when the intent is to add a step between two elements that are already connected. Specify the target flow either by sequenceFlowId, or by giving sourceElementId + targetElementId (the tool finds the flow between them). The original flow is reconnected as source -> newElement and a new flow newElement -> target is created; any condition expression on the original flow is preserved on the source -> newElement segment. Returns validation errors after applying the change. Supported types (flow nodes only): userTask, serviceTask, sendTask, receiveTask, businessRuleTask, manualTask, scriptTask, intermediateThrowEvent, intermediateCatchEvent, exclusiveGateway, inclusiveGateway, parallelGateway, eventBasedGateway, subProcess, adHocSubProcess, callActivity. startEvent, endEvent, and boundaryEvent cannot be inserted on a flow.",
    parameters: {
      type: "object",
      properties: {
        type: {
          type: "string",
          description: "The BPMN element type to insert (e.g., 'userTask', 'serviceTask', 'exclusiveGateway')"
        },
        label: {
          type: "string",
          description: "Optional display name for the new element"
        },
        sequenceFlowId: {
          type: "string",
          description: "ID of the sequence flow to split. Use this OR sourceElementId+targetElementId."
        },
        sourceElementId: {
          type: "string",
          description: "ID of the source element of the flow to split. Requires targetElementId."
        },
        targetElementId: {
          type: "string",
          description: "ID of the target element of the flow to split. Requires sourceElementId."
        },
        eventDefinition: {
          type: "string",
          description: "Optional event definition for intermediate events: 'message', 'timer', 'signal', 'error', 'escalation', 'compensation', 'conditional', 'link'"
        }
      },
      required: ["type"]
    },
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    handler: async (r, a) => {
      try {
        return await n((i) => {
          const s = e.modeler.getModeling(), o = e.modeler.getElementFactory(), l = i.get("bpmnRules"), c = vi(r.type), u = Zm(r, e), d = th(r, c), p = o.createShape(d), m = eh(u);
          if (!l.canInsert(p, u, m))
            throw new Error(
              `Cannot insert '${r.type}' on flow ${u.id}. Only flow nodes (tasks, gateways, intermediate events, subprocesses) can be inserted on a sequence flow.`
            );
          const h = s.createShape(p, m, u);
          r.label && s.updateProperties(h, { name: r.label });
          const g = u.source?.id, v = u.target?.id;
          return {
            elementId: h.id,
            type: h.type,
            label: r.label ?? "",
            splitSequenceFlowId: u.id,
            sourceElementId: g,
            targetElementId: v
          };
        });
      } catch (i) {
        throw a("insert_bpmn_element_between", i), new Error(`Failed to insert BPMN element: ${i.message}`);
      }
    }
  };
}, ah = Object.keys(It), rh = (e, t, n, r) => {
  if (!r.modeler.is(e, "bpmn:Event"))
    throw new Error(`eventDefinition is only valid on bpmn:Event elements, got ${e.type}`);
  const a = n.get("moddle"), i = n.get("modeling"), s = r.modeler.getBusinessObject(e), o = t === null || t === "" ? [] : (() => {
    const l = It[t];
    if (!l)
      throw new Error(
        `Unsupported eventDefinition: '${t}'. Expected one of: ${ah.join(
          ", "
        )} (or null to clear).`
      );
    const c = a.create(l, {});
    return c.$parent = s, [c];
  })();
  i.updateProperties(e, { eventDefinitions: o });
}, ih = (e, t, n, r) => {
  if (!r.modeler.is(e, "bpmn:Event"))
    return;
  const a = (r.modeler.getBusinessObject(e).get("eventDefinitions") ?? []).find((i) => r.modeler.is(i, "bpmn:LinkEventDefinition"));
  a && n.get("modeling").updateModdleProperties(e, a, { name: t });
}, sh = (e) => {
  const t = Be(e);
  return {
    name: "update_bpmn_element",
    description: "Updates properties of an existing BPMN element: name (display label) and/or eventDefinition (for bpmn:Event elements only — replaces any existing event definitions with a single new one, or pass null to strip them). For link events, setting name also cascades into the LinkEventDefinition name so the `link-event` bpmnlint rule can resolve a throw/catch pair via name matching. For Zeebe extension properties (task definition, I/O mappings, headers, timer time-definitions, conditional-event condition), use set_element_properties. Returns validation errors after applying the change.",
    parameters: {
      type: "object",
      properties: {
        elementId: {
          type: "string",
          description: "The ID of the BPMN element to update"
        },
        name: {
          type: "string",
          description: "New display name for the element. For link events, this value is also written to the LinkEventDefinition name."
        },
        eventDefinition: {
          type: ["string", "null"],
          description: "For bpmn:Event elements: replace the event's existing event definitions with a single new one. One of: 'message', 'timer', 'signal', 'error', 'escalation', 'compensation', 'conditional', 'link', 'terminate'. Pass null (or an empty string) to clear all event definitions (blank event)."
        }
      },
      required: ["elementId"]
    },
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: (n) => {
      const r = Qe(n.name);
      return r ? `Renaming to "${r}"` : "Updating element";
    },
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = e.modeler.getModeler(), i = a.get("modeling"), s = e.modeler.getElementById(n.elementId);
          n.name !== void 0 && (i.updateProperties(s, { name: n.name }), ih(s, n.name, a, e)), n.eventDefinition !== void 0 && rh(s, n.eventDefinition, a, e);
          const o = e.modeler.getBusinessObject(s), l = {};
          if (n.name !== void 0 && (l.name = o.get("name")), n.eventDefinition !== void 0) {
            const c = o.get("eventDefinitions") ?? [];
            l.eventDefinition = c.length === 0 ? null : c[0].$type.replace(/^bpmn:/, "").replace(/EventDefinition$/, "").toLowerCase();
          }
          return { elementId: s.id, updated: l };
        });
      } catch (a) {
        throw r("update_bpmn_element", a), new Error(`Failed to update BPMN element: ${a.message}`);
      }
    }
  };
}, oh = (e, t) => {
  const n = t.modeler.getElementRegistry(), r = e.id;
  return n.filter((a) => {
    if (!t.modeler.is(a, "bpmn:BoundaryEvent"))
      return !1;
    const i = a.host;
    if (i && i.id === r)
      return !0;
    const s = t.modeler.getBusinessObject(a);
    return (typeof s.get == "function" ? s.get("attachedToRef") : s.attachedToRef)?.id === r;
  });
}, lh = (e) => {
  const t = Be(e);
  return {
    name: "delete_bpmn_element",
    description: "Deletes a BPMN element from the diagram. If the element has exactly one incoming and one outgoing sequence flow, the surrounding elements are automatically reconnected. If the element has boundary events attached to it, those boundary events are deleted first so the XML never contains boundary events pointing at a removed host (the deleted IDs are returned in `cascadedBoundaryEventIds`). Returns validation errors after applying the change.",
    parameters: {
      type: "object",
      properties: {
        elementId: {
          type: "string",
          description: "The ID of the BPMN element to delete"
        }
      },
      required: ["elementId"]
    },
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: () => "Removing element",
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = e.modeler.getModeling(), i = e.modeler.getElementById(n.elementId), s = i.id, o = oh(i, e), l = o.map((g) => g.id);
          o.length > 0 && a.removeElements(o);
          const c = (i.incoming ?? []).filter((g) => e.modeler.is(g, "bpmn:SequenceFlow")), u = (i.outgoing ?? []).filter((g) => e.modeler.is(g, "bpmn:SequenceFlow")), d = c.length === 1 && u.length === 1, p = d ? c[0].source : void 0, m = d ? u[0].target : void 0;
          a.removeElements([i]);
          const h = !!(d && p && m);
          return h && a.connect(p, m), { elementId: s, reconnected: h, cascadedBoundaryEventIds: l };
        });
      } catch (a) {
        throw r("delete_bpmn_element", a), new Error(`Failed to delete BPMN element: ${a.message}`);
      }
    }
  };
}, ch = (e) => {
  const t = Be(e);
  return {
    name: "move_bpmn_element",
    description: "Moves a BPMN element to a new position in the process flow by reconnecting sequence flows. The element is disconnected from its current neighbors (which are reconnected to each other) and connected at the new position. At least one of afterElementId or beforeElementId is required. Returns validation errors after applying the change.",
    parameters: {
      type: "object",
      properties: {
        elementId: {
          type: "string",
          description: "The ID of the BPMN element to move"
        },
        afterElementId: {
          type: "string",
          description: "ID of the element that should be the new source (incoming flow)"
        },
        beforeElementId: {
          type: "string",
          description: "ID of the element that should be the new target (outgoing flow)"
        }
      },
      required: ["elementId"]
    },
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: () => "Reorganizing flow",
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = n.afterElementId, i = n.beforeElementId;
          if (!a && !i)
            throw new Error("At least one of afterElementId or beforeElementId must be provided");
          const s = e.modeler.getModeling(), o = e.modeler.getElementById(n.elementId), l = (o.incoming ?? []).filter((p) => e.modeler.is(p, "bpmn:SequenceFlow")), c = (o.outgoing ?? []).filter((p) => e.modeler.is(p, "bpmn:SequenceFlow"));
          if (l.length > 1 || c.length > 1)
            throw new Error(
              `Cannot safely move ${n.elementId}: it has ${l.length} incoming and ${c.length} outgoing sequence flows. move_bpmn_element only supports elements with at most one incoming and one outgoing flow. Use delete_sequence_flow / add_sequence_flow to rewire the surrounding flows manually.`
            );
          const u = l.map((p) => p.source), d = c.map((p) => p.target);
          if (s.removeElements([...l, ...c]), u.length === 1 && d.length === 1 && s.connect(u[0], d[0]), a) {
            const p = e.modeler.getElementById(a);
            s.connect(p, o);
          }
          if (i) {
            const p = e.modeler.getElementById(i);
            s.connect(o, p);
          }
          return {
            elementId: o.id,
            newPosition: {
              afterElementId: a ?? null,
              beforeElementId: i ?? null
            }
          };
        });
      } catch (a) {
        throw r("move_bpmn_element", a), new Error(`Failed to move BPMN element: ${a.message}`);
      }
    }
  };
}, uh = (e) => {
  const t = Be(e);
  return {
    name: "add_sequence_flow",
    description: "Creates a new sequence flow connection between two BPMN elements. Optionally sets a label and/or condition expression. Returns validation errors after applying the change.",
    parameters: {
      type: "object",
      properties: {
        sourceId: {
          type: "string",
          description: "The ID of the source element (flow starts here)"
        },
        targetId: {
          type: "string",
          description: "The ID of the target element (flow ends here)"
        },
        label: {
          type: "string",
          description: "Optional label for the sequence flow"
        },
        conditionExpression: {
          type: "string",
          description: "Optional FEEL condition expression for conditional flows (e.g., after exclusive gateways)"
        }
      },
      required: ["sourceId", "targetId"]
    },
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: () => "Connecting elements",
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = e.modeler.getModeling(), i = e.modeler.getModdle(), s = e.modeler.getElementById(n.sourceId), o = e.modeler.getElementById(n.targetId), l = (s.outgoing ?? []).find(
            (u) => e.modeler.is(u, "bpmn:SequenceFlow") && u.target?.id === o.id
          );
          if (l)
            throw new Error(
              `A sequence flow already exists from ${n.sourceId} to ${n.targetId} (${l.id}). Use update_sequence_flow to update its label or condition instead of creating a duplicate.`
            );
          const c = a.connect(s, o);
          if (n.label && a.updateProperties(c, { name: n.label }), n.conditionExpression) {
            const u = i.create("bpmn:FormalExpression", {
              body: n.conditionExpression
            });
            a.updateProperties(c, { conditionExpression: u });
          }
          return {
            flowId: c.id,
            sourceId: n.sourceId,
            targetId: n.targetId
          };
        });
      } catch (a) {
        throw r("add_sequence_flow", a), new Error(`Failed to add sequence flow: ${a.message}`);
      }
    }
  };
}, dh = (e) => {
  const t = Be(e);
  return {
    name: "update_sequence_flow",
    description: "Updates properties of an existing sequence flow (label and/or condition expression). Returns validation errors after applying the change.",
    parameters: {
      type: "object",
      properties: {
        flowId: {
          type: "string",
          description: "The ID of the sequence flow to update"
        },
        label: {
          type: "string",
          description: "New label for the sequence flow"
        },
        conditionExpression: {
          type: "string",
          description: "New FEEL condition expression (set to empty string to remove condition)"
        }
      },
      required: ["flowId"]
    },
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: (n) => {
      if (n.conditionExpression !== void 0)
        return n.conditionExpression === "" ? "Clearing flow condition" : "Setting flow condition";
      const r = Qe(n.label);
      return r ? `Labeling "${r}"` : "Updating connection";
    },
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = e.modeler.getElementById(n.flowId);
          if (!e.modeler.is(a, "bpmn:SequenceFlow"))
            throw new Error(`Element ${n.flowId} is not a sequence flow`);
          const i = e.modeler.getModeling(), s = e.modeler.getModdle(), o = {
            ...n.label !== void 0 ? { name: n.label } : {},
            ...n.conditionExpression !== void 0 ? {
              conditionExpression: n.conditionExpression ? s.create("bpmn:FormalExpression", {
                body: n.conditionExpression
              }) : void 0
            } : {}
          };
          return Object.keys(o).length > 0 && i.updateProperties(a, o), {
            flowId: a.id,
            updated: {
              label: n.label,
              conditionExpression: n.conditionExpression
            }
          };
        });
      } catch (a) {
        throw r("update_sequence_flow", a), new Error(`Failed to update sequence flow: ${a.message}`);
      }
    }
  };
}, ph = (e) => {
  const t = Be(e);
  return {
    name: "delete_sequence_flow",
    description: "Deletes a sequence flow connection from the diagram. Returns validation errors after applying the change.",
    parameters: {
      type: "object",
      properties: {
        flowId: {
          type: "string",
          description: "The ID of the sequence flow to delete"
        }
      },
      required: ["flowId"]
    },
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: () => "Removing connection",
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = e.modeler.getModeling(), i = e.modeler.getElementById(n.flowId);
          if (!e.modeler.is(i, "bpmn:SequenceFlow"))
            throw new Error(`Element ${n.flowId} is not a sequence flow`);
          const s = i.id;
          return a.removeElements([i]), { flowId: s };
        });
      } catch (a) {
        throw r("delete_sequence_flow", a), new Error(`Failed to delete sequence flow: ${a.message}`);
      }
    }
  };
}, mh = (e) => {
  const t = Be(e);
  return {
    name: "bind_form_to_task",
    description: 'Binds a Camunda form to a user task using the zeebe:formDefinition extension. Provide formId (the "id" field from inside the form schema JSON, NOT the file UUID returned by create_file_in_project) or formKey (for external/embedded forms). Returns validation errors after applying the change.',
    parameters: {
      type: "object",
      properties: {
        taskId: {
          type: "string",
          description: "The ID of the user task to bind the form to"
        },
        formId: {
          type: "string",
          description: 'The form schema ID (the "id" field from inside the form schema JSON, NOT the file UUID). Example: "my-intake-form", not "0f111b82-db24-..."'
        },
        formKey: {
          type: "string",
          description: "The external form key (for custom or embedded forms)"
        },
        bindingType: {
          type: "string",
          description: "The binding type: 'latest' (default), 'deployment', or 'versionTag'"
        }
      },
      required: ["taskId"]
    },
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: () => "Binding a form",
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = e.modeler.getElementById(n.taskId);
          if (!e.modeler.is(a, "bpmn:UserTask"))
            throw new Error(`Element ${n.taskId} is not a user task`);
          const i = {
            ...n.formId ? { formId: n.formId } : {},
            ...n.formKey ? { formKey: n.formKey } : {},
            ...n.bindingType ? { bindingType: n.bindingType } : {}
          };
          return Ft(
            a,
            "zeebe:FormDefinition",
            i,
            e.modeler.getModeler(),
            e.modeler.getBusinessObject,
            e.modeler.is
          ), {
            taskId: a.id,
            binding: {
              formId: n.formId ?? null,
              formKey: n.formKey ?? null,
              bindingType: n.bindingType ?? "latest"
            }
          };
        });
      } catch (a) {
        throw r("bind_form_to_task", a), new Error(`Failed to bind form to task: ${a.message}`);
      }
    }
  };
}, hh = {
  timeDuration: "bpmn:FormalExpression",
  timeCycle: "bpmn:FormalExpression",
  timeDate: "bpmn:FormalExpression"
}, Je = (e, t) => {
  const n = e;
  return typeof n.get == "function" ? n.get(t) : n[t];
}, fh = (e, t, n, r) => {
  if (!r.modeler.is(e, "bpmn:Event"))
    throw new Error(
      `timerDefinition is only valid on bpmn:Event elements (start, intermediate, boundary), got ${e.type}`
    );
  const { type: a, expression: i } = t, s = hh[a];
  if (!s)
    throw new Error(`Unsupported timerDefinition.type: '${a}'. Expected one of: timeDuration, timeCycle, timeDate.`);
  if (i == null || i === "")
    throw new Error("timerDefinition.expression is required and must be a non-empty string");
  const o = n.get("moddle"), l = n.get("modeling"), c = r.modeler.getBusinessObject(e), u = Je(c, "eventDefinitions") ?? [], d = u.find(
    (m) => r.modeler.is(m, "bpmn:TimerEventDefinition")
  ) ?? (() => {
    const m = o.create("bpmn:TimerEventDefinition", {});
    return m.$parent = c, l.updateProperties(e, {
      eventDefinitions: [...u, m]
    }), m;
  })(), p = o.create(s, { body: i });
  p.$parent = d, l.updateModdleProperties(e, d, {
    timeDuration: a === "timeDuration" ? p : void 0,
    timeCycle: a === "timeCycle" ? p : void 0,
    timeDate: a === "timeDate" ? p : void 0
  });
}, or = (e, t, n, r) => {
  const a = /* @__PURE__ */ new Map();
  return e.forEach((i) => {
    a.set(Je(i, "target"), i);
  }), t.forEach((i) => {
    a.set(i.target, n.create(r, { source: i.source, target: i.target }));
  }), Array.from(a.values());
}, gh = (e, t, n) => {
  const r = /* @__PURE__ */ new Map();
  return e.forEach((a) => {
    r.set(Je(a, "key"), a);
  }), Object.entries(t).forEach(([a, i]) => {
    r.set(a, n.create("zeebe:Header", { key: a, value: i }));
  }), Array.from(r.values());
}, yh = (e, t, n) => {
  const r = /* @__PURE__ */ new Map();
  return e.forEach((a) => {
    r.set(Je(a, "name"), a);
  }), Object.entries(t).forEach(([a, i]) => {
    r.set(a, n.create("zeebe:Property", { name: a, value: i }));
  }), Array.from(r.values());
}, bh = (e, t, n, r, a) => {
  const i = r.get("moddle"), s = a.modeler.getBusinessObject(e), o = n ? null : ot(s, "zeebe:IoMapping", a.modeler.is), l = (o && Je(o, "inputParameters")) ?? [], c = (o && Je(o, "outputParameters")) ?? [], u = {
    ...t.inputs ? { inputParameters: or(l, t.inputs, i, "zeebe:Input") } : {},
    ...t.outputs ? { outputParameters: or(c, t.outputs, i, "zeebe:Output") } : {}
  };
  Ft(
    e,
    "zeebe:IoMapping",
    u,
    r,
    a.modeler.getBusinessObject,
    a.modeler.is
  );
}, vh = (e, t, n, r, a) => {
  const i = r.get("moddle"), s = a.modeler.getBusinessObject(e), o = n ? null : ot(s, "zeebe:TaskHeaders", a.modeler.is), l = (o && Je(o, "values")) ?? [], c = gh(l, t, i);
  Ft(
    e,
    "zeebe:TaskHeaders",
    { values: c },
    r,
    a.modeler.getBusinessObject,
    a.modeler.is
  );
}, kh = (e, t, n, r, a) => {
  const i = r.get("moddle"), s = a.modeler.getBusinessObject(e), o = n ? null : ot(s, "zeebe:Properties", a.modeler.is), l = (o && Je(o, "properties")) ?? [], c = yh(l, t, i);
  Ft(
    e,
    "zeebe:Properties",
    { properties: c },
    r,
    a.modeler.getBusinessObject,
    a.modeler.is
  );
}, wh = (e, t, n, r) => {
  if (!r.modeler.is(e, "bpmn:Event"))
    throw new Error(
      `conditionExpression on set_element_properties targets bpmn:ConditionalEventDefinition on events; got ${e.type}. For sequence flows, use update_sequence_flow(flowId, conditionExpression).`
    );
  const a = n.get("moddle"), i = n.get("modeling"), s = r.modeler.getBusinessObject(e), o = (Je(s, "eventDefinitions") ?? []).find((c) => r.modeler.is(c, "bpmn:ConditionalEventDefinition"));
  if (!o)
    throw new Error(
      `Element ${e.id} has no bpmn:ConditionalEventDefinition. Add one first with update_bpmn_element(elementId, eventDefinition: "conditional").`
    );
  const l = t === "" || t === null ? void 0 : (() => {
    const c = a.create("bpmn:FormalExpression", { body: t });
    return c.$parent = o, c;
  })();
  i.updateModdleProperties(e, o, { condition: l });
}, xh = (e) => {
  const t = Be(e);
  return {
    name: "set_element_properties",
    description: "Sets Zeebe extension properties and BPMN structural properties on a BPMN element: task definition (type/retries), I/O mappings (inputs/outputs), task headers, custom Zeebe properties, timer event definitions, and conditional-event conditions. I/O mappings, task headers, and Zeebe properties are MERGED by key by default (target for I/O, key for headers, name for properties) — pass replaceIoMappings / replaceTaskHeaders / replaceZeebeProperties = true to clobber instead. For sequence-flow conditions, use update_sequence_flow; this tool targets events and activities. Returns validation errors after applying the change.",
    parameters: {
      type: "object",
      properties: {
        elementId: {
          type: "string",
          description: "The ID of the BPMN element"
        },
        taskDefinition: {
          type: "object",
          description: "Task definition with 'type' (job type) and optional 'retries' (e.g., '3')",
          properties: {
            type: { type: "string", description: "The job type for the task worker" },
            retries: { type: "string", description: "Number of retries (default: '3')" }
          }
        },
        ioMappings: {
          type: "object",
          description: "Input/output variable mappings. Merged by target against any existing mappings on the element; pass replaceIoMappings=true to replace the full ioMapping block instead.",
          properties: {
            inputs: {
              type: "array",
              description: "Input mappings: FEEL source expression → target variable name",
              items: {
                type: "object",
                properties: {
                  source: { type: "string", description: "FEEL source expression" },
                  target: { type: "string", description: "Target variable name in the task" }
                }
              }
            },
            outputs: {
              type: "array",
              description: "Output mappings: FEEL source expression → target variable name",
              items: {
                type: "object",
                properties: {
                  source: { type: "string", description: "FEEL source expression" },
                  target: { type: "string", description: "Target variable name in the process" }
                }
              }
            }
          }
        },
        replaceIoMappings: {
          type: "boolean",
          description: "If true, replace the full zeebe:IoMapping block instead of merging inputs/outputs by target. Default: false."
        },
        taskHeaders: {
          type: "object",
          description: "Key-value map of task headers. Merged by key against any existing headers on the element; pass replaceTaskHeaders=true to replace the full header block instead.",
          additionalProperties: { type: "string" }
        },
        replaceTaskHeaders: {
          type: "boolean",
          description: "If true, replace the full zeebe:TaskHeaders block instead of merging by key. Default: false."
        },
        zeebeProperties: {
          type: "object",
          description: "Key-value map of custom Zeebe properties. Merged by name against any existing properties on the element; pass replaceZeebeProperties=true to replace the full properties block instead.",
          additionalProperties: { type: "string" }
        },
        replaceZeebeProperties: {
          type: "boolean",
          description: "If true, replace the full zeebe:Properties block instead of merging by name. Default: false."
        },
        timerDefinition: {
          type: "object",
          description: "Timer event definition body for start/intermediate catch/boundary events. Writes <bpmn:timeDuration> / <bpmn:timeCycle> / <bpmn:timeDate> under the event's <bpmn:timerEventDefinition>. Creates the timerEventDefinition if the event does not have one yet.",
          properties: {
            type: {
              type: "string",
              description: "One of: 'timeDuration' (ISO 8601 duration, e.g. 'PT15M'), 'timeCycle' (ISO 8601 repeating interval or cron, e.g. 'R/PT1H' or '0 0 9 ? * MON-FRI'), 'timeDate' (ISO 8601 date-time, e.g. '2026-01-01T00:00:00Z')"
            },
            expression: {
              type: "string",
              description: `The timer expression. Can be a FEEL expression (e.g. '=duration("PT15M")') or a static ISO 8601 value.`
            }
          },
          required: ["type", "expression"]
        },
        conditionExpression: {
          type: ["string", "null"],
          description: `FEEL condition body to write under this event's <bpmn:conditionalEventDefinition>. Target element must already be an event with a conditionalEventDefinition — if it is not, first call update_bpmn_element(elementId, eventDefinition: "conditional"). Pass "" or null to clear the existing condition. For sequence flows use update_sequence_flow(flowId, conditionExpression) instead.`
        }
      },
      required: ["elementId"]
    },
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: (n) => n.taskDefinition ? "Setting job configuration" : n.ioMappings ? "Setting input/output mappings" : n.taskHeaders ? "Adding task headers" : n.timerDefinition ? "Setting timer definition" : n.conditionExpression !== void 0 ? "Setting condition expression" : "Configuring properties",
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = e.modeler.getModeler(), i = e.modeler.getElementById(n.elementId);
          return n.taskDefinition && Ft(
            i,
            "zeebe:TaskDefinition",
            n.taskDefinition,
            a,
            e.modeler.getBusinessObject,
            e.modeler.is
          ), n.ioMappings && bh(
            i,
            n.ioMappings,
            n.replaceIoMappings === !0,
            a,
            e
          ), n.taskHeaders && vh(
            i,
            n.taskHeaders,
            n.replaceTaskHeaders === !0,
            a,
            e
          ), n.zeebeProperties && kh(
            i,
            n.zeebeProperties,
            n.replaceZeebeProperties === !0,
            a,
            e
          ), n.timerDefinition && fh(i, n.timerDefinition, a, e), n.conditionExpression !== void 0 && wh(i, n.conditionExpression, a, e), { elementId: i.id };
        });
      } catch (a) {
        throw r("set_element_properties", a), new Error(`Failed to set element properties: ${a.message}`);
      }
    }
  };
}, Eh = (e) => {
  const t = Be(e);
  return {
    name: "undo",
    description: "Undoes the last change made to the BPMN diagram. Returns validation errors after applying the change.",
    parameters: {},
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: () => "Undoing last change",
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = e.modeler.getCommandStack();
          return a.canUndo() ? (a.undo(), { success: !0 }) : { success: !1, message: "Nothing to undo" };
        });
      } catch (a) {
        throw r("undo", a), new Error(`Failed to undo: ${a.message}`);
      }
    }
  };
}, _h = (e) => {
  const t = Be(e);
  return {
    name: "redo",
    description: "Redoes the last undone change to the BPMN diagram. Returns validation errors after applying the change.",
    parameters: {},
    type: "frontend",
    contentType: "TEXT",
    category: "BPMN_MODIFICATION",
    requiredCapabilities: ["BPMN_EDITING"],
    descriptionFormatter: () => "Redoing last change",
    handler: async (n, r) => {
      try {
        return await t(() => {
          const a = e.modeler.getCommandStack();
          return a.canRedo() ? (a.redo(), { success: !0 }) : { success: !1, message: "Nothing to redo" };
        });
      } catch (a) {
        throw r("redo", a), new Error(`Failed to redo: ${a.message}`);
      }
    }
  };
}, Ih = (e) => ({
  name: "read_artifact",
  description: "Reads the current artifact content. Returns form JSON if a form editor is active, BPMN XML if a BPMN diagram is open, or DMN XML if a DMN diagram is open.",
  parameters: {},
  type: "frontend",
  contentType: "TEXT",
  category: "FILE_OPERATION",
  requiredCapabilities: ["FILE_OPERATIONS"],
  descriptionFormatter: () => "Reading diagram",
  handler: async (t, n) => {
    try {
      if (e.form.isAvailable())
        return { formJson: e.form.getContent() };
      if (e.diagram.isBPMN())
        return { bpmnXml: await e.diagram.exportXml() };
      if (e.diagram.isDMN())
        return { dmnXml: await e.diagram.exportXml() };
      throw new Error("No artifact available to read. Open a BPMN, DMN, or Form file first.");
    } catch (r) {
      throw n("read_artifact", r), r;
    }
  }
}), Jn = (e) => {
  if (!e.diagram.canEdit())
    throw new Error("Cannot edit: the current diagram is read-only.");
}, Ch = async (e, t) => {
  Jn(e);
  const { error: n } = await e.diagram.importContent(t);
  if (n)
    throw new Error(`Failed to import BPMN: ${n instanceof Error ? n.message : String(n)}`);
  return await e.diagram.saveContent(), { validationErrors: await e.diagram.getLintErrors() };
}, Th = async (e, t) => {
  Jn(e);
  const n = e.form.getParsedContent(t);
  return e.form.importSchema(n), await e.form.update(), { validationErrors: await e.form.getLintErrors() };
}, Sh = async (e, t) => {
  Jn(e);
  const { error: n } = await e.diagram.importContent(t);
  if (n)
    throw new Error(`Failed to import DMN: ${n instanceof Error ? n.message : String(n)}`);
  return await e.diagram.saveContent(), { validationErrors: [] };
}, Rh = (e) => ({
  name: "write_artifact",
  description: "Writes content to the current artifact. Accepts bpmnXml, formJson, or dmnXml. Validates the content after writing and returns any validation errors.",
  parameters: {
    type: "object",
    properties: {
      bpmnXml: { type: "string", description: "BPMN XML content to write" },
      formJson: { type: "string", description: "Form JSON content to write" },
      dmnXml: { type: "string", description: "DMN XML content to write" }
    }
  },
  type: "frontend",
  contentType: "TEXT",
  category: "FILE_OPERATION",
  requiredCapabilities: ["FILE_OPERATIONS"],
  descriptionFormatter: () => "Writing changes",
  handler: async (t, n) => {
    try {
      if (t.bpmnXml)
        return await Ch(e, t.bpmnXml);
      if (t.formJson)
        return await Th(e, t.formJson);
      if (t.dmnXml)
        return await Sh(e, t.dmnXml);
      throw new Error("No content provided. Pass bpmnXml, formJson, or dmnXml.");
    } catch (r) {
      throw n("write_artifact", r), r;
    }
  }
}), Nh = (e) => ({
  name: "sync_artifact_state_ui",
  description: "Saves the current diagram state to persist any pending changes in the UI.",
  parameters: {},
  type: "frontend",
  contentType: "TEXT",
  category: "FILE_OPERATION",
  requiredCapabilities: ["FILE_OPERATIONS"],
  displayName: "Saving changes",
  descriptionFormatter: () => "Saving changes",
  handler: async (t, n) => {
    try {
      return await e.diagram.saveContent(), { success: !0 };
    } catch (r) {
      throw n("sync_artifact_state_ui", r), r;
    }
  }
}), Ah = (e) => ({
  name: "create_file_in_project",
  description: "Creates a new file in the current project. Supports BPMN, DMN, Form, and Connector Template file types.",
  parameters: {
    type: "object",
    properties: {
      name: { type: "string", description: "Name of the file to create" },
      type: {
        type: "string",
        enum: ["BPMN", "DMN", "FORM", "CONNECTOR_TEMPLATE"],
        description: "Type of the file to create"
      },
      content: { type: "string", description: "Content of the file" }
    },
    required: ["name", "type", "content"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "FILE_OPERATION",
  requiredCapabilities: ["FILE_CREATION"],
  descriptionFormatter: (t) => Qe(t.name) ? `Creating "${Qe(t.name)}"` : "Creating file",
  handler: async (t, n) => {
    try {
      if (!e.diagram.canEdit())
        throw new Error("Cannot create files: the current context is read-only.");
      const { id: r, folderId: a } = e.project.resolve(), { id: i } = await e.files.create({
        name: t.name,
        type: t.type,
        content: t.content,
        projectId: r,
        folderId: a
      });
      return {
        fileId: i,
        name: t.name,
        type: t.type
      };
    } catch (r) {
      throw n("create_file_in_project", r), r;
    }
  }
}), Mh = (e) => ({
  name: "get_file_content_from_file_id",
  description: "Retrieves the content of a file by its ID from the project.",
  parameters: {
    type: "object",
    properties: {
      fileId: { type: "string", description: "The ID of the file to read" }
    },
    required: ["fileId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "FILE_OPERATION",
  requiredCapabilities: ["FILE_OPERATIONS"],
  descriptionFormatter: () => "Reading file",
  handler: async (t, n) => {
    try {
      return { content: await e.files.getContent(t.fileId) };
    } catch (r) {
      throw n("get_file_content_from_file_id", r), r;
    }
  }
}), Oh = 8e3, Ph = (e, t, n) => new Promise((r, a) => {
  const i = setTimeout(() => {
    a(new Error(n));
  }, t);
  e.then((s) => {
    clearTimeout(i), r(s);
  }).catch((s) => {
    clearTimeout(i), a(s);
  });
}), Dh = (e) => ({
  name: "list_files_in_project",
  description: "Lists all files in the current project.",
  parameters: {},
  type: "frontend",
  contentType: "TEXT",
  category: "FILE_OPERATION",
  requiredCapabilities: ["FILE_OPERATIONS"],
  descriptionFormatter: () => "Listing project files",
  handler: async (t, n) => {
    try {
      const { id: r } = e.project.resolve(), a = await Ph(
        e.files.list(r),
        Oh,
        "Timed out while listing project files"
      );
      return { files: a, totalCount: a.length };
    } catch (r) {
      throw n("list_files_in_project", r), r;
    }
  }
}), lr = (e, t) => e.map((n) => ({ ...n, source: t })), jh = (e) => {
  const t = [...e].sort((n, r) => n.id.localeCompare(r.id) || r.version - n.version);
  return t.filter((n, r) => r === 0 || t[r - 1].id !== n.id);
}, Bh = (e, t) => {
  const n = t.toLowerCase(), r = (e.name ?? "").toLowerCase(), a = (e.description ?? "").toLowerCase();
  return r.includes(n) || a.includes(n);
}, zh = (e, t) => !t || e.appliesTo.some((n) => n.toLowerCase().includes(t.toLowerCase())), Lh = (e) => ({
  name: "search_element_templates",
  description: "Searches available element templates (connectors and custom templates) by name or description. Returns matching templates with their metadata. Use this to discover available integrations and connector types that can be applied to BPMN elements.",
  parameters: {
    type: "object",
    properties: {
      searchTerm: {
        type: "string",
        description: "The search term to filter templates by name or description"
      },
      elementType: {
        type: "string",
        description: "Optional BPMN element type to filter templates that apply to this type"
      }
    },
    required: ["searchTerm"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "INTEGRATION",
  requiredCapabilities: ["BPMN_VIEWING", "INTEGRATION"],
  descriptionFormatter: (t) => Qe(t.searchTerm) ? `Searching templates for "${Qe(t.searchTerm)}"` : "Browsing templates",
  handler: async (t, n) => {
    try {
      const r = lr(e.templates.getOotbConnectors(), "ootb"), a = lr(e.templates.getCustom(), "custom"), i = [...r, ...a], s = jh(i).filter((o) => Bh(o, t.searchTerm)).filter((o) => zh(o, t.elementType)).map((o) => ({
        id: o.id,
        name: o.name,
        description: o.description ?? "",
        latestVersion: o.version,
        appliesTo: o.appliesTo,
        elementType: o.elementType ?? null,
        source: o.source
      }));
      return { templates: s, totalCount: s.length };
    } catch (r) {
      throw n("search_element_templates", r), new Error(`Failed to search element templates: ${r.message}`);
    }
  }
}), Fh = (e) => e.reduce(
  (t, n) => !t || n.version > t.version ? n : t,
  void 0
), qh = (e, t) => e.find((n) => n.version === t), $h = (e) => ({
  name: "get_element_template_details",
  description: "Gets detailed information about a specific element template, including its properties, groups, and configuration. Use this after searching templates to understand what a template configures before applying it.",
  parameters: {
    type: "object",
    properties: {
      templateId: {
        type: "string",
        description: "The ID of the element template to get details for"
      },
      version: {
        type: "number",
        description: "Optional specific version to retrieve. If omitted, returns the latest version."
      }
    },
    required: ["templateId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "INTEGRATION",
  requiredCapabilities: ["BPMN_VIEWING", "INTEGRATION"],
  descriptionFormatter: () => "Loading template details",
  handler: async (t, n) => {
    try {
      const r = [...e.templates.getOotbConnectors(), ...e.templates.getCustom()].filter((i) => i.id === t.templateId);
      if (r.length === 0)
        throw new Error(`Template with ID "${t.templateId}" not found`);
      const a = t.version !== void 0 ? qh(r, t.version) : Fh(r);
      if (!a)
        throw new Error(`Template "${t.templateId}" version ${t.version} not found`);
      return {
        id: a.id,
        name: a.name,
        description: a.description ?? "",
        version: a.version,
        appliesTo: a.appliesTo,
        elementType: a.elementType ?? null,
        groups: a.groups ?? [],
        properties: a.properties ?? []
      };
    } catch (r) {
      throw n("get_element_template_details", r), new Error(`Failed to get template details: ${r.message}`);
    }
  }
}), Uh = (e) => ({
  name: "apply_element_template",
  description: "Applies an element template (connector or custom template) to a BPMN element. The element must exist in the diagram and the template must be available. Use search_element_templates first to find the template ID.",
  parameters: {
    type: "object",
    properties: {
      elementId: {
        type: "string",
        description: "The ID of the BPMN element to apply the template to"
      },
      templateId: {
        type: "string",
        description: "The ID of the element template to apply"
      },
      version: {
        type: "number",
        description: "Optional specific template version. If omitted, applies the latest version."
      }
    },
    required: ["elementId", "templateId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "INTEGRATION",
  requiredCapabilities: ["BPMN_EDITING", "INTEGRATION"],
  descriptionFormatter: () => "Applying connector template",
  handler: async (t, n) => {
    try {
      if (!e.diagram.canEdit())
        throw new Error("Diagram is not editable");
      return e.modeler.getElementById(t.elementId), e.templates.apply(t.elementId, t.templateId, t.version), await e.diagram.saveContent(), {
        elementId: t.elementId,
        templateId: t.templateId,
        success: !0
      };
    } catch (r) {
      throw n("apply_element_template", r), new Error(`Failed to apply element template: ${r.message}`);
    }
  }
}), Gh = (e) => ({
  name: "get_diagram_errors",
  description: "Gets all validation errors for the current BPMN diagram, including lint errors and deployment errors. Use this to check if the diagram is valid and identify issues that need to be fixed.",
  parameters: {},
  type: "frontend",
  contentType: "TEXT",
  category: "BPMN_QUERY",
  requiredCapabilities: ["BPMN_VIEWING", "VALIDATION"],
  descriptionFormatter: () => "Checking diagram errors",
  handler: async (t, n) => {
    try {
      const r = await e.diagram.getLintErrors(), a = e.diagram.getDeploymentErrors();
      return {
        lintErrors: r,
        deploymentErrors: a,
        totalCount: r.length + a.length
      };
    } catch (r) {
      throw n("get_diagram_errors", r), new Error(`Failed to get diagram errors: ${r.message}`);
    }
  }
}), Hh = (e) => ({
  name: "get_form_errors",
  description: "Gets all validation errors for the current form. Returns lint errors that indicate issues with form field configuration. Only available when a form is open.",
  parameters: {},
  type: "frontend",
  contentType: "TEXT",
  category: "FORM_CORE",
  requiredCapabilities: ["FORM_VIEWING", "VALIDATION"],
  descriptionFormatter: () => "Checking form errors",
  handler: async (t, n) => {
    try {
      if (!e.form.isAvailable())
        throw new Error("Form is not available in the current context");
      const r = await e.form.getLintErrors();
      return {
        lintErrors: r,
        totalCount: r.length
      };
    } catch (r) {
      throw n("get_form_errors", r), new Error(`Failed to get form errors: ${r.message}`);
    }
  }
}), Vh = (e) => ({
  name: "layout_bpmn_xml",
  description: "Auto-layouts the current BPMN diagram to improve visual clarity and readability.",
  parameters: {},
  type: "frontend",
  contentType: "BPMN",
  category: "BPMN_CORE",
  requiredCapabilities: ["BPMN_EDITING"],
  displayName: "Laying out diagram",
  descriptionFormatter: () => "Laying out diagram",
  handler: async (t, n) => {
    try {
      if (!e.diagram.canEdit())
        throw new Error("Cannot layout: the current diagram is read-only.");
      const r = await e.diagram.exportXml(), a = await bi(r), { error: i } = await e.diagram.importContent(a);
      if (i)
        throw new Error(`Auto-layout import failed: ${i instanceof Error ? i.message : String(i)}`);
      return e.diagram.ensureExecutionPlatform(), await e.diagram.saveContent(), { bpmnXml: a };
    } catch (r) {
      throw n("layout_bpmn_xml", r), r;
    }
  }
}), Bn = (e) => typeof e == "object" && e !== null && typeof e.$type == "string", zn = (e, t) => t(e) === !1 ? !1 : Object.keys(e).every((n) => {
  if (n.startsWith("$"))
    return !0;
  const r = e[n];
  return Array.isArray(r) ? r.every((a) => Bn(a) ? zn(a, t) : !0) : Bn(r) ? zn(r, t) : !0;
}), ki = (e, t) => {
  Bn(e) && zn(e, t);
}, Ct = (e) => `${e}_${Math.random().toString(36).slice(2, 10)}`, Ze = async (e, t) => {
  const { rootElement: n } = await e.fromXML(t);
  return { definitions: n };
}, St = async (e) => {
  const { xml: t } = await e.$model.toXML(e, { format: !0 });
  return t;
}, ht = (e, t) => {
  const n = (e.drgElement ?? []).filter((a) => a.$type === "dmn:Decision"), r = t ? n.find((a) => a.id === t) : n[0];
  if (!r)
    throw new Error(`Decision not found: ${t ?? "first decision"}`);
  return r;
}, ft = (e) => {
  const t = e.decisionLogic;
  if (!t || t.$type !== "dmn:DecisionTable")
    throw new Error(`Decision ${e.id} does not have a decision table`);
  return t;
}, Kh = (e, t, n, r) => {
  const a = e.name ?? null;
  if (e.$type === "dmn:Decision") {
    const i = e.decisionLogic;
    t.push({
      id: e.id,
      name: a,
      hasDecisionTable: i?.$type === "dmn:DecisionTable"
    });
    return;
  }
  if (e.$type === "dmn:InputData") {
    n.push({ id: e.id, name: a });
    return;
  }
  e.$type === "dmn:BusinessKnowledgeModel" && r.push({ id: e.id, name: a });
}, Wh = (e) => {
  const t = [], n = [], r = [];
  return ki(
    e,
    (a) => Kh(a, t, n, r)
  ), {
    definitionsId: e.id ?? null,
    decisionsCount: t.length,
    inputDataCount: n.length,
    businessKnowledgeModelCount: r.length,
    decisions: t,
    inputData: n,
    businessKnowledgeModels: r
  };
}, Xh = (e) => ({
  name: "get_dmn_summary",
  displayName: "Reading DMN summary",
  description: "Returns an overview of the current DMN model including decisions, input data, and business knowledge models.",
  parameters: {},
  type: "frontend",
  contentType: "TEXT",
  category: "DMN_QUERY",
  requiredCapabilities: ["DMN_VIEWING"],
  handler: async (t, n) => {
    try {
      const r = await e.diagram.exportXml(), a = e.dmn.createModdle(), { definitions: i } = await Ze(a, r);
      return Wh(i);
    } catch (r) {
      throw n("get_dmn_summary", r), new Error(`Failed to get DMN summary: ${r.message}`);
    }
  }
}), Qh = (e) => ({
  name: "get_dmn_element_details",
  displayName: "Reading DMN element",
  description: "Returns metadata for a DMN element by id.",
  parameters: {
    type: "object",
    properties: {
      elementId: {
        type: "string",
        description: "The DMN element id to inspect"
      }
    },
    required: ["elementId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "DMN_QUERY",
  requiredCapabilities: ["DMN_VIEWING"],
  handler: async (t, n) => {
    try {
      const { elementId: r } = t, a = await e.diagram.exportXml(), i = e.dmn.createModdle(), { definitions: s } = await Ze(i, a), o = [];
      ki(s, (c) => {
        if (c.id === r)
          return o.push(c), !1;
      });
      const l = o[0];
      if (!l)
        throw new Error(`DMN element not found: ${r}`);
      return {
        id: l.id ?? null,
        type: l.$type ?? null,
        name: l.name ?? null,
        label: l.label ?? null
      };
    } catch (r) {
      throw n("get_dmn_element_details", r), new Error(`Failed to get DMN element details: ${r.message}`);
    }
  }
}), Jh = (e) => e.map((t) => {
  const n = t.inputExpression;
  return {
    id: t.id,
    label: t.label ?? null,
    expression: n?.text ?? "",
    typeRef: n?.typeRef ?? null
  };
}), Yh = (e) => e.map((t) => ({
  id: t.id,
  label: t.label ?? null,
  name: t.name ?? null,
  typeRef: t.typeRef ?? null
})), Zh = (e) => e.map((t) => ({
  id: t.id,
  inputEntries: (t.inputEntry ?? []).map(
    (n) => n.text ?? ""
  ),
  outputEntries: (t.outputEntry ?? []).map(
    (n) => n.text ?? ""
  )
})), ef = (e) => ({
  name: "get_decision_table",
  displayName: "Reading decision table",
  description: "Returns the full decision table structure for a decision, including inputs, outputs, hit policy, and rules.",
  parameters: {
    type: "object",
    properties: {
      decisionId: {
        type: "string",
        description: "Optional decision id. If omitted, the first decision table is used."
      }
    }
  },
  type: "frontend",
  contentType: "TEXT",
  category: "DMN_QUERY",
  requiredCapabilities: ["DMN_VIEWING"],
  handler: async (t, n) => {
    try {
      const { decisionId: r } = t, a = await e.diagram.exportXml(), i = e.dmn.createModdle(), { definitions: s } = await Ze(i, a), o = ht(s, r), l = ft(o), c = l.input ?? [], u = l.output ?? [], d = l.rule ?? [];
      return {
        decisionId: o.id,
        decisionName: o.name ?? null,
        tableId: l.id,
        hitPolicy: l.hitPolicy ?? "UNIQUE",
        inputs: Jh(c),
        outputs: Yh(u),
        rules: Zh(d)
      };
    } catch (r) {
      throw n("get_decision_table", r), new Error(`Failed to get decision table: ${r.message}`);
    }
  }
}), tf = /* @__PURE__ */ new Set(["UNIQUE", "FIRST", "PRIORITY", "ANY", "COLLECT", "RULE ORDER", "OUTPUT ORDER"]), nf = (e) => ({
  name: "set_hit_policy",
  displayName: "Setting hit policy",
  description: "Sets the hit policy of a DMN decision table.",
  parameters: {
    type: "object",
    properties: {
      decisionId: {
        type: "string",
        description: "Optional decision id. If omitted, the first decision table is used."
      },
      hitPolicy: {
        type: "string",
        description: "DMN hit policy (UNIQUE, FIRST, PRIORITY, ANY, COLLECT, RULE ORDER, OUTPUT ORDER)"
      }
    },
    required: ["hitPolicy"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "DMN_MODIFICATION",
  requiredCapabilities: ["DMN_EDITING"],
  handler: async (t, n) => {
    try {
      const { decisionId: r, hitPolicy: a } = t;
      if (!tf.has(a))
        throw new Error(`Unsupported hit policy: ${a}`);
      const i = await e.diagram.exportXml(), s = e.dmn.createModdle(), { definitions: o } = await Ze(s, i), l = ht(o, r), c = ft(l);
      c.hitPolicy = a;
      const u = await St(o), { error: d } = await e.diagram.importContent(u);
      if (d)
        throw new Error(`Failed to import DMN: ${String(d)}`);
      return await e.diagram.saveContent(), {
        decisionId: l.id,
        tableId: c.id,
        hitPolicy: a
      };
    } catch (r) {
      throw n("set_hit_policy", r), new Error(`Failed to set hit policy: ${r.message}`);
    }
  }
}), af = (e) => ({
  name: "update_dmn_xml",
  displayName: "Updating DMN XML",
  description: "Imports and saves full DMN XML content for advanced edits.",
  parameters: {
    type: "object",
    properties: {
      dmnXml: {
        type: "string",
        description: "Complete DMN XML content to import"
      }
    },
    required: ["dmnXml"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "DMN_MODIFICATION",
  requiredCapabilities: ["DMN_EDITING"],
  handler: async (t, n) => {
    try {
      const { dmnXml: r } = t;
      if (!r || typeof r != "string")
        throw new Error("dmnXml must be a non-empty string");
      const { error: a } = await e.diagram.importContent(r);
      if (a)
        throw new Error(`Failed to import DMN: ${String(a)}`);
      return await e.diagram.saveContent(), {
        success: !0,
        message: "DMN XML updated successfully"
      };
    } catch (r) {
      throw n("update_dmn_xml", r), new Error(`Failed to update DMN XML: ${r.message}`);
    }
  }
}), rf = (e, { label: t, expression: n, typeRef: r }) => e.create("dmn:Input", {
  id: Ct("Input"),
  label: t ?? null,
  inputExpression: e.create("dmn:LiteralExpression", {
    id: Ct("InputExpression"),
    text: n ?? "",
    typeRef: r ?? "string"
  })
}), sf = (e, { label: t, expression: n, typeRef: r }) => e.create("dmn:OutputClause", {
  id: Ct("Output"),
  label: t ?? null,
  name: n ?? null,
  typeRef: r ?? "string"
}), of = (e, t, n) => {
  if (n.type === "input") {
    const r = rf(e, n), a = t.input ?? [];
    return t.input = [...a, r], r;
  }
  if (n.type === "output") {
    const r = sf(e, n), a = t.output ?? [];
    return t.output = [...a, r], r;
  }
  throw new Error("Column type must be either 'input' or 'output'");
}, lf = (e, { label: t, expression: n, typeRef: r }) => {
  const a = e;
  t !== void 0 && (a.label = t);
  const i = e.inputExpression;
  n !== void 0 && i && (i.text = n), r !== void 0 && i && (i.typeRef = r);
}, cf = (e, { label: t, expression: n, typeRef: r }) => {
  const a = e;
  t !== void 0 && (a.label = t), n !== void 0 && (a.name = n), r !== void 0 && (a.typeRef = r);
}, uf = (e, t) => {
  const n = (e.input ?? []).find((a) => a.id === t.columnId);
  if (n)
    return lf(n, t), { type: "input", column: n };
  const r = (e.output ?? []).find((a) => a.id === t.columnId);
  if (r)
    return cf(r, t), { type: "output", column: r };
  throw new Error(`Column not found: ${t.columnId}`);
}, df = (e) => ({
  name: "add_decision_table_column",
  displayName: "Adding decision table column",
  description: "Adds an input or output column to a DMN decision table.",
  parameters: {
    type: "object",
    properties: {
      decisionId: {
        type: "string",
        description: "Optional decision id. If omitted, the first decision table is used."
      },
      type: {
        type: "string",
        description: "Column type: 'input' or 'output'"
      },
      label: {
        type: "string",
        description: "Column label"
      },
      expression: {
        type: "string",
        description: "Input expression (input columns) or output name (output columns)"
      },
      typeRef: {
        type: "string",
        description: "Type reference, e.g. string, number, boolean"
      }
    },
    required: ["type"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "DMN_MODIFICATION",
  requiredCapabilities: ["DMN_EDITING"],
  handler: async (t, n) => {
    try {
      const { decisionId: r, type: a, label: i, expression: s, typeRef: o } = t, l = await e.diagram.exportXml(), c = e.dmn.createModdle(), { definitions: u } = await Ze(c, l), d = ht(u, r), p = ft(d), m = of(c, p, { type: a, label: i, expression: s, typeRef: o }), h = await St(u), { error: g } = await e.diagram.importContent(h);
      if (g)
        throw new Error(`Failed to import DMN: ${String(g)}`);
      return await e.diagram.saveContent(), {
        decisionId: d.id,
        tableId: p.id,
        columnId: m.id,
        columnType: a
      };
    } catch (r) {
      throw n("add_decision_table_column", r), new Error(`Failed to add decision table column: ${r.message}`);
    }
  }
}), wi = (e, t) => e.create("dmn:UnaryTests", {
  id: Ct("InputEntry"),
  text: t
}), xi = (e, t) => e.create("dmn:LiteralExpression", {
  id: Ct("OutputEntry"),
  text: t
}), pf = (e, t, n = [], r = []) => {
  const a = e.create("dmn:DecisionRule", {
    id: Ct("Rule"),
    inputEntry: n.map((s) => wi(e, s ?? "")),
    outputEntry: r.map((s) => xi(e, s ?? ""))
  }), i = t.rule ?? [];
  return t.rule = [...i, a], a;
}, mf = (e, t, n, r = [], a = []) => {
  const i = (t.rule ?? []).find((s) => s.id === n);
  if (!i)
    throw new Error(`Rule not found: ${n}`);
  return r.length > 0 && (i.inputEntry = r.map((s) => wi(e, s ?? ""))), a.length > 0 && (i.outputEntry = a.map((s) => xi(e, s ?? ""))), i;
}, hf = (e, t) => {
  const n = e.rule ?? [], r = n.filter((a) => a.id !== t);
  if (r.length === n.length)
    throw new Error(`Rule not found: ${t}`);
  e.rule = r;
}, ff = (e) => ({
  name: "add_decision_table_rule",
  displayName: "Adding decision table rule",
  description: "Adds a rule to a DMN decision table.",
  parameters: {
    type: "object",
    properties: {
      decisionId: {
        type: "string",
        description: "Optional decision id. If omitted, the first decision table is used."
      },
      inputEntries: {
        type: "array",
        items: { type: "string" },
        description: "Input entry expressions in table column order."
      },
      outputEntries: {
        type: "array",
        items: { type: "string" },
        description: "Output entry expressions in table column order."
      }
    }
  },
  type: "frontend",
  contentType: "TEXT",
  category: "DMN_MODIFICATION",
  requiredCapabilities: ["DMN_EDITING"],
  handler: async (t, n) => {
    try {
      const {
        decisionId: r,
        inputEntries: a = [],
        outputEntries: i = []
      } = t, s = await e.diagram.exportXml(), o = e.dmn.createModdle(), { definitions: l } = await Ze(o, s), c = ht(l, r), u = ft(c), d = pf(o, u, a, i), p = await St(l), { error: m } = await e.diagram.importContent(p);
      if (m)
        throw new Error(`Failed to import DMN: ${String(m)}`);
      return await e.diagram.saveContent(), {
        decisionId: c.id,
        tableId: u.id,
        ruleId: d.id
      };
    } catch (r) {
      throw n("add_decision_table_rule", r), new Error(`Failed to add decision table rule: ${r.message}`);
    }
  }
}), gf = (e) => ({
  name: "delete_decision_table_rule",
  displayName: "Deleting decision table rule",
  description: "Deletes a rule from a DMN decision table.",
  parameters: {
    type: "object",
    properties: {
      decisionId: {
        type: "string",
        description: "Optional decision id. If omitted, the first decision table is used."
      },
      ruleId: {
        type: "string",
        description: "Rule id to delete"
      }
    },
    required: ["ruleId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "DMN_MODIFICATION",
  requiredCapabilities: ["DMN_EDITING"],
  handler: async (t, n) => {
    try {
      const { decisionId: r, ruleId: a } = t, i = await e.diagram.exportXml(), s = e.dmn.createModdle(), { definitions: o } = await Ze(s, i), l = ht(o, r), c = ft(l);
      hf(c, a);
      const u = await St(o), { error: d } = await e.diagram.importContent(u);
      if (d)
        throw new Error(`Failed to import DMN: ${String(d)}`);
      return await e.diagram.saveContent(), {
        decisionId: l.id,
        tableId: c.id,
        deletedRuleId: a
      };
    } catch (r) {
      throw n("delete_decision_table_rule", r), new Error(`Failed to delete decision table rule: ${r.message}`);
    }
  }
}), yf = (e) => ({
  name: "update_decision_table_column",
  displayName: "Updating decision table column",
  description: "Updates an input or output column in a DMN decision table.",
  parameters: {
    type: "object",
    properties: {
      decisionId: {
        type: "string",
        description: "Optional decision id. If omitted, the first decision table is used."
      },
      columnId: {
        type: "string",
        description: "Column id to update"
      },
      label: {
        type: "string",
        description: "Updated column label"
      },
      expression: {
        type: "string",
        description: "Updated expression (inputExpression.text or output name)"
      },
      typeRef: {
        type: "string",
        description: "Updated type reference"
      }
    },
    required: ["columnId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "DMN_MODIFICATION",
  requiredCapabilities: ["DMN_EDITING"],
  handler: async (t, n) => {
    try {
      const { decisionId: r, columnId: a, label: i, expression: s, typeRef: o } = t, l = await e.diagram.exportXml(), c = e.dmn.createModdle(), { definitions: u } = await Ze(c, l), d = ht(u, r), p = ft(d), { type: m } = uf(p, { columnId: a, label: i, expression: s, typeRef: o }), h = await St(u), { error: g } = await e.diagram.importContent(h);
      if (g)
        throw new Error(`Failed to import DMN: ${String(g)}`);
      return await e.diagram.saveContent(), {
        decisionId: d.id,
        tableId: p.id,
        columnId: a,
        columnType: m
      };
    } catch (r) {
      throw n("update_decision_table_column", r), new Error(`Failed to update decision table column: ${r.message}`);
    }
  }
}), bf = (e) => ({
  name: "update_decision_table_rule",
  displayName: "Updating decision table rule",
  description: "Updates an existing DMN decision table rule.",
  parameters: {
    type: "object",
    properties: {
      decisionId: {
        type: "string",
        description: "Optional decision id. If omitted, the first decision table is used."
      },
      ruleId: {
        type: "string",
        description: "Rule id to update"
      },
      inputEntries: {
        type: "array",
        items: { type: "string" },
        description: "Replacement input entries in table column order."
      },
      outputEntries: {
        type: "array",
        items: { type: "string" },
        description: "Replacement output entries in table column order."
      }
    },
    required: ["ruleId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "DMN_MODIFICATION",
  requiredCapabilities: ["DMN_EDITING"],
  handler: async (t, n) => {
    try {
      const {
        decisionId: r,
        ruleId: a,
        inputEntries: i = [],
        outputEntries: s = []
      } = t, o = await e.diagram.exportXml(), l = e.dmn.createModdle(), { definitions: c } = await Ze(l, o), u = ht(c, r), d = ft(u), p = mf(l, d, a, i, s), m = await St(c), { error: h } = await e.diagram.importContent(m);
      if (h)
        throw new Error(`Failed to import DMN: ${String(h)}`);
      return await e.diagram.saveContent(), {
        decisionId: u.id,
        tableId: d.id,
        ruleId: p.id
      };
    } catch (r) {
      throw n("update_decision_table_rule", r), new Error(`Failed to update decision table rule: ${r.message}`);
    }
  }
}), vf = (e) => ({
  name: "get_form_schema",
  displayName: "Reading form schema",
  description: "Returns the current form schema from the active form editor session.",
  parameters: {},
  type: "frontend",
  contentType: "TEXT",
  category: "FORM_CORE",
  requiredCapabilities: ["FORM_VIEWING"],
  handler: async (t, n) => {
    try {
      return e.form.getSchema();
    } catch (r) {
      throw n("get_form_schema", r), new Error(`Failed to read form schema: ${r.message}`);
    }
  }
}), kf = (e) => ({
  name: "get_form_binding_from_task",
  displayName: "Reading form binding",
  description: "Gets the form binding configuration from a BPMN user task, including formId/formKey and binding details.",
  parameters: {
    type: "object",
    properties: {
      taskId: {
        type: "string",
        description: "The ID of the BPMN user task"
      }
    },
    required: ["taskId"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "FORM_CORE",
  requiredCapabilities: ["BPMN_VIEWING"],
  handler: async (t, n) => {
    try {
      const { taskId: r } = t, a = e.modeler.getElementById(r);
      if (!e.modeler.is(a, "bpmn:UserTask"))
        throw new Error(`Element ${r} is not a user task`);
      const i = (e.modeler.getBusinessObject(a)?.extensionElements?.values ?? []).find((s) => s.$type === "zeebe:FormDefinition");
      return i ? {
        taskId: r,
        found: !0,
        formId: i.formId ?? null,
        formKey: i.formKey ?? null,
        bindingType: i.bindingType ?? "latest",
        version: i.version ?? i.versionTag ?? null
      } : {
        taskId: r,
        found: !1,
        message: "No form binding found for task"
      };
    } catch (r) {
      throw n("get_form_binding_from_task", r), new Error(`Failed to get form binding from task: ${r.message}`);
    }
  }
}), wf = (e) => {
  if (!e)
    return null;
  const t = Array.isArray(e) ? e[0] : e;
  if (!t || typeof t != "object")
    return null;
  const n = t;
  return {
    id: typeof n.id == "string" ? n.id : null,
    key: typeof n.key == "string" ? n.key : null,
    type: typeof n.type == "string" ? n.type : null,
    label: typeof n.label == "string" ? n.label : null
  };
}, xf = (e) => ({
  name: "get_selected_form_field",
  displayName: "Reading selected form field",
  description: "Returns details of the currently selected field in the form editor.",
  parameters: {},
  type: "frontend",
  contentType: "TEXT",
  category: "FORM_CORE",
  requiredCapabilities: ["FORM_VIEWING"],
  handler: async (t, n) => {
    try {
      const r = e.form.getSelection(), a = wf(r);
      return {
        selected: a,
        found: !!a
      };
    } catch (r) {
      throw n("get_selected_form_field", r), new Error(`Failed to get selected form field: ${r.message}`);
    }
  }
}), Ef = /* @__PURE__ */ new Set([
  "textfield",
  "textarea",
  "number",
  "datetime",
  "checkbox",
  "radio",
  "select",
  "checklist",
  "taglist",
  "text",
  "image",
  "spacer",
  "separator",
  "group",
  "button",
  "default"
]), Ln = /* @__PURE__ */ new Set(["text", "image", "spacer", "separator", "group", "button", "default"]), we = (e) => e !== null && typeof e == "object" && !Array.isArray(e), Yn = (e, t) => !we(e) || !we(t) ? t : Object.entries(t).reduce(
  (n, [r, a]) => we(a) && we(n[r]) ? { ...n, [r]: Yn(n[r], a) } : { ...n, [r]: a },
  { ...e }
), Ei = (e, t) => {
  if (e == null || e === "")
    return null;
  if (typeof e == "string")
    try {
      const n = JSON.parse(e);
      if (!we(n))
        throw new Error(`${t} must be a JSON object`);
      return n;
    } catch (n) {
      throw n instanceof SyntaxError ? new Error(`${t} must be valid JSON`) : n;
    }
  if (!we(e))
    throw new Error(`${t} must be a JSON object`);
  return e;
}, _f = (e) => {
  const t = e.id;
  if (typeof t == "string")
    return t;
  const n = e.key;
  return typeof n == "string" ? n : null;
}, Zn = (e, t) => Array.isArray(e) ? e.map((n) => {
  if (!we(n))
    return n;
  const r = Array.isArray(n.components) ? { ...n, components: Zn(n.components, t) } : { ...n };
  return t(r);
}).filter((n) => n != null) : [], If = (e, t) => {
  if (!Array.isArray(t) || t.length === 0)
    return e;
  const n = new Set(
    t.map((a) => we(a) ? a.id : void 0).filter((a) => typeof a == "string")
  ), r = new Set(
    t.map((a) => we(a) ? a.key : void 0).filter((a) => typeof a == "string")
  );
  return Zn(e, (a) => {
    const i = a.id, s = a.key;
    return typeof i == "string" && n.has(i) || typeof s == "string" && r.has(s) ? null : a;
  });
}, Cf = (e, t) => !Array.isArray(t) || t.length === 0 ? e : Zn(e, (n) => {
  const r = t.find((a) => we(a) ? a.id !== void 0 && a.id === n.id || a.key !== void 0 && a.key === n.key : !1);
  return r ? Yn(n, r) : n;
}), ea = (e) => {
  const t = Ei(e, "formJson");
  if (!t)
    throw new Error("formJson must not be empty");
  return t;
}, Tf = (e, t) => {
  const n = Ei(t, "changes");
  if (!n)
    throw new Error("changes must not be empty");
  const { addComponents: r = [], removeComponents: a = [], components: i = [], ...s } = n, o = Yn({ ...e }, s), l = Array.isArray(o.components) ? [...o.components] : [], c = If(l, a), u = Cf(c, i), d = Array.isArray(r) && r.length > 0 ? [...u, ...r] : u;
  return { ...o, components: d };
}, Sf = (e) => {
  const t = {}, n = [], r = { totalFields: 0, requiredFields: 0, optionalFields: 0 }, a = (i) => {
    Array.isArray(i) && i.forEach((s) => {
      if (!we(s))
        return;
      const o = typeof s.type == "string" ? s.type : "default", l = _f(s), c = typeof s.key == "string" ? s.key : l, u = s.validate, d = !!(we(u) && u.required || s.required);
      Ln.has(o) || (r.totalFields += 1, d ? r.requiredFields += 1 : r.optionalFields += 1, t[o] = (t[o] ?? 0) + 1, n.push({
        key: c,
        type: o,
        label: typeof s.label == "string" ? s.label : null,
        required: d,
        hasDefaultValue: s.defaultValue !== void 0
      })), a(s.components);
    });
  };
  return a(e.components), {
    schemaVersion: e.schemaVersion,
    totalFields: r.totalFields,
    requiredFields: r.requiredFields,
    optionalFields: r.optionalFields,
    fieldsByType: t,
    fields: n
  };
}, _i = (e) => {
  const t = [], n = [], r = /* @__PURE__ */ new Set();
  if (!we(e))
    return { isValid: !1, errors: ["Form schema must be a JSON object"], warnings: n };
  typeof e.schemaVersion != "number" && t.push("schemaVersion is required and must be a number"), Array.isArray(e.components) || t.push("components is required and must be an array");
  const a = (i, s = 0) => {
    if (Array.isArray(i)) {
      if (s > 100) {
        t.push("Component nesting depth exceeds maximum of 100");
        return;
      }
      i.forEach((o) => {
        if (!we(o)) {
          t.push("Component entries must be objects");
          return;
        }
        const l = typeof o.type == "string" ? o.type : "default", c = o.id;
        typeof c == "string" && (r.has(c) ? t.push(`Duplicate component id: ${c}`) : r.add(c)), Ef.has(l) || t.push(`Invalid field type "${l}"`);
        const u = o.key;
        if (!Ln.has(l) && !u && t.push(`Input component of type "${l}" requires a "key" field`), !Ln.has(l) && !o.label) {
          const d = typeof u == "string" && u || typeof c == "string" && c || l;
          n.push(`Input component "${d}" has no label`);
        }
        a(o.components, s + 1);
      });
    }
  };
  return a(e.components), {
    isValid: t.length === 0,
    errors: t,
    warnings: n
  };
}, Rf = (e) => ({
  name: "summarize_form_json",
  displayName: "Summarizing form schema",
  description: "Summarizes a Camunda Form JSON definition with field counts, required field distribution, and field details.",
  parameters: {
    type: "object",
    properties: {
      formJson: {
        type: "string",
        description: "The Form JSON content to summarize. If omitted, summarizes the currently open form."
      }
    }
  },
  type: "frontend",
  contentType: "TEXT",
  category: "FORM_CORE",
  requiredCapabilities: ["FORM_VIEWING"],
  handler: async (t, n) => {
    try {
      const { formJson: r } = t ?? {}, a = r ? ea(r) : e.form.getSchema();
      return Sf(a);
    } catch (r) {
      throw n("summarize_form_json", r), new Error(`Failed to summarize form JSON: ${r.message}`);
    }
  }
}), Nf = (e) => ({
  name: "update_form_json",
  displayName: "Updating form schema",
  description: "Updates a Camunda Form JSON definition with structured changes such as add, update, and remove operations.",
  parameters: {
    type: "object",
    properties: {
      formJson: {
        type: "string",
        description: "The original Form JSON. If omitted, the currently open form schema is used."
      },
      changes: {
        type: "string",
        description: "A JSON object describing updates (addComponents, components, removeComponents, or top-level fields)."
      }
    },
    required: ["changes"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "FORM_CORE",
  requiredCapabilities: ["FORM_EDITING"],
  handler: async (t, n) => {
    try {
      const { formJson: r, changes: a } = t, i = r ? ea(r) : e.form.getSchema(), s = Tf(i, a), o = _i(s);
      return e.form.importSchema(s), await e.form.update(), {
        success: !0,
        message: "Form JSON updated successfully",
        isValid: o.isValid,
        validationErrors: o.errors,
        validationWarnings: o.warnings,
        formJson: JSON.stringify(s, null, 2)
      };
    } catch (r) {
      throw n("update_form_json", r), new Error(`Failed to update form JSON: ${r.message}`);
    }
  }
}), Af = (e) => ({
  name: "validate_form_json",
  displayName: "Validating form schema",
  description: "Validates a Camunda Form JSON definition for structural correctness, required fields, and duplicate IDs.",
  parameters: {
    type: "object",
    properties: {
      formJson: {
        type: "string",
        description: "The Form JSON content to validate. If omitted, validates the currently open form."
      }
    }
  },
  type: "frontend",
  contentType: "TEXT",
  category: "FORM_CORE",
  requiredCapabilities: ["VALIDATION", "FORM_VIEWING"],
  handler: async (t, n) => {
    try {
      const { formJson: r } = t ?? {}, a = r ? ea(r) : e.form.getSchema();
      return _i(a);
    } catch (r) {
      throw n("validate_form_json", r), new Error(`Failed to validate form JSON: ${r.message}`);
    }
  }
}), Mf = (e) => {
  if (typeof e != "string")
    return "";
  const t = e.trim();
  return t.startsWith("=") ? t.slice(1).trim() : t;
}, Of = (e) => ({
  name: "validate_feel_expression",
  displayName: "Validating FEEL expression",
  description: "Validates a FEEL expression for syntax correctness. Use this before applying FEEL expressions to BPMN or DMN elements.",
  parameters: {
    type: "object",
    properties: {
      feelExpression: {
        type: "string",
        description: "The FEEL expression to validate. The optional '=' prefix is accepted."
      }
    },
    required: ["feelExpression"]
  },
  type: "frontend",
  contentType: "TEXT",
  category: "FEEL_CORE",
  requiredCapabilities: ["VALIDATION"],
  handler: async (t, n) => {
    try {
      const { feelExpression: r } = t, a = Mf(r);
      if (!a)
        return {
          isValid: !1,
          errors: ["FEEL expression must not be empty"],
          warnings: [],
          expression: a
        };
      const i = await e.feel.validate(a);
      return i?.error ? {
        isValid: !1,
        errors: [i.error],
        warnings: i.warnings ?? [],
        expression: a
      } : {
        isValid: !0,
        errors: [],
        warnings: i?.warnings ?? [],
        expression: a
      };
    } catch (r) {
      throw n("validate_feel_expression", r), new Error(`Failed to validate FEEL expression: ${r.message}`);
    }
  }
}), Pf = (e) => [
  wm(e),
  Em(e),
  Im(e),
  Cm(e),
  Mm(e),
  Pm(e),
  Lm(e),
  Qm(e),
  nh(e, bi),
  sh(e),
  lh(e),
  ch(e),
  uh(e),
  dh(e),
  ph(e),
  mh(e),
  xh(e),
  Eh(e),
  _h(e),
  Ih(e),
  Rh(e),
  Nh(e),
  Ah(e),
  Mh(e),
  Dh(e),
  Lh(e),
  $h(e),
  Uh(e),
  Gh(e),
  Hh(e),
  Vh(e),
  Xh(e),
  Qh(e),
  ef(e),
  nf(e),
  af(e),
  df(e),
  ff(e),
  gf(e),
  yf(e),
  bf(e),
  vf(e),
  kf(e),
  xf(e),
  Rf(e),
  Nf(e),
  Af(e),
  Of(e)
], og = (e, t) => {
  for (const n of Pf(e))
    t.registerTool(n);
}, lg = ({ getModeler: e, is: t, getBusinessObject: n }) => ({
  getModeler: e,
  getCanvas: () => e().get("canvas"),
  getElementRegistry: () => e().get("elementRegistry"),
  getModeling: () => e().get("modeling"),
  getCommandStack: () => e().get("commandStack"),
  getElementFactory: () => e().get("elementFactory"),
  getModdle: () => e().get("moddle"),
  getSelection: () => e().get("selection"),
  getElementById: (r) => {
    const a = e().get("elementRegistry").get(r);
    if (!a)
      throw new Error(`Element not found: ${r}`);
    return a;
  },
  is: t,
  getBusinessObject: n,
  getBpmnRules: () => e().get("bpmnRules")
}), Df = /* @__PURE__ */ new Set(["external-resources/validate"]), jf = /* @__PURE__ */ new Set([
  "superfluous-gateway",
  "fake-join",
  "no-implicit-split",
  "no-implicit-end",
  "no-implicit-start",
  "no-disconnected",
  "no-duplicate-sequence-flows",
  "no-gateway-join-fork",
  "end-event-required",
  "start-event-required"
]), cg = (e) => e.filter((t) => !Df.has(t.rule)).map((t) => ({
  elementId: t.id,
  elementName: t.name ?? "",
  message: t.message,
  category: jf.has(t.rule) ? "error" : t.category,
  rule: t.rule,
  documentationUrl: t.meta?.documentation?.url
}));
export {
  Wf as AgentState,
  rg as CAPABILITY,
  ll as ChatInput,
  Pl as ChatMessage,
  nc as ChatMessageList,
  Qf as CopilotChat,
  fc as CopilotHeader,
  Jf as CopilotSidecar,
  oe as EventStatus,
  H as EventType,
  cl as MarkdownRenderer,
  vu as QueryCancelledError,
  ln as ResultType,
  Tl as ThinkingIndicator,
  cg as adaptBpmnLintErrorsForLlm,
  sg as camundaDocsSearchTool,
  Zf as closeSidecar,
  Pf as createAllTools,
  lg as createModelerAccess,
  Ym as createWithAutoLayoutAndValidation,
  Be as createWithValidation,
  Wr as getDefaultStatusLabel,
  ud as isApplyElementTemplateCompleted,
  ng as isCurrentFileModified,
  cd as isLayoutBpmnXmlCompleted,
  ig as isToolAllowedWithCapabilities,
  ld as isWriteArtifactCompleted,
  bi as layoutProcess,
  Yf as openSidecar,
  Xf as selectAgentState,
  Mc as selectIsBusy,
  og as setupBpmnTools,
  eg as toggleSidecar,
  Ee as toolRegistry,
  ki as traverseModdle,
  ag as useAgentAdapter,
  G as useAgentStore,
  F as useChatStore,
  tg as useSidecarOpen
};
