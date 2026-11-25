import { loadAll, repairLine, hasDamage, words } from "./tukaram.mjs";
import { getText } from "./wslib.mjs";
const all = await loadAll();
for (const a of all) a.fixed = a.corrupted ? a.lines.map(repairLine) : a.lines;
const dmg = (a) => a.fixed.some(hasDamage);
console.log("damaged:", all.filter(dmg).length, "/", all.length, " (clean-page damaged:", all.filter(a => !a.corrupted && dmg(a)).length + ")");
const V = new Set(); for (const a of all.filter(a => !a.corrupted && !dmg(a))) for (const w of words(a.fixed.join(" "))) V.add(w);
const F = new Map(); for (const a of all) if (!dmg(a)) for (const w of words(a.fixed.join(" "))) F.set(w, (F.get(w) || 0) + 1);
for (const th of [2, 3]) {
  let pass = 0; for (const a of all) { if (dmg(a)) continue; if (words(a.fixed.join(" ")).every(w => V.has(w) || (F.get(w) || 0) >= th)) pass++; }
  console.log("threshold", th, "->", pass, "abhangs with every word trusted");
}
const undamaged = all.filter(a => !dmg(a)).length; console.log("undamaged total", undamaged);
