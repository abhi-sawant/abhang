import { loadAll, repairLine, hasDamage, words } from "./tukaram.mjs";
const all = await loadAll();
for (const a of all) a.fixed = a.corrupted ? a.lines.map(repairLine) : a.lines;
const dmg = all.filter(a => a.fixed.some(hasDamage));
console.log("damaged:", dmg.length, "/", all.length);
const ok = all.filter(a => a.corrupted && !a.fixed.some(hasDamage));
let seed = 7; const rnd = () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff;
for (let i = 0; i < 14; i++) { const a = ok[Math.floor(rnd() * ok.length)]; console.log("\n#" + a.n + " [" + a.section + "]\n" + a.fixed.join("\n")); }
