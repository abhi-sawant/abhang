import { loadAll, repairLine, hasDamage, words } from "./tukaram.mjs";
const all = await loadAll();
for (const a of all) a.fixed = a.corrupted ? a.lines.map(repairLine) : a.lines;
const dev = (c) => c >= "ऀ" && c <= "ॿ";
const bg = (w) => { const r = []; for (let i = 0; i < w.length - 1; i++) if (dev(w[i]) && dev(w[i+1])) r.push(w[i] + w[i+1]); return r; };
const clean = new Map();
for (const a of all.filter(a => !a.corrupted)) for (const w of words(a.fixed.join(" "))) for (const b of bg(w)) clean.set(b, (clean.get(b)||0)+1);
const nov = new Map(), ex = new Map();
for (const a of all.filter(a => a.corrupted && !a.fixed.some(hasDamage))) for (const w of words(a.fixed.join(" "))) for (const b of bg(w)) if (!clean.has(b)) { nov.set(b, (nov.get(b)||0)+1); if (!ex.has(b)) ex.set(b, new Set()); if (ex.get(b).size < 4) ex.get(b).add(w); }
const top = [...nov].sort((x,y)=>y[1]-x[1]).slice(0, 60);
console.log("novel bigrams:", nov.size);
for (const [b,n] of top) console.log(JSON.stringify(b), [...b].map(c=>c.codePointAt(0).toString(16)).join("+"), n, [...ex.get(b)].join(" "));
