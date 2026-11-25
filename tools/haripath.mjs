import fs from "node:fs";
const RAW = new URL("../content/raw/", import.meta.url);
const D = "०१२३४५६७८९";
const fixChak = (s) => s.replace(/ॆ/g, "े").replace(/ॊ/g, "ो").replace(/ऎ/g, "ए").replace(/ऒ/g, "ओ").replace(/ऻ/g, "").replace(/\s+\./g, " ।").replace(/\.\./g, "॥");
export function mahiti() {
  const t = fs.readFileSync(new URL("hp_mahiti.txt", RAW), "utf8");
  const parts = t.split(/\n\s*~\s*([०-९]+)\s*~\s*\n/);
  const out = [];
  for (let i = 1; i < parts.length; i += 2) {
    const n = Number([...parts[i]].map(c => D.indexOf(c)).join(""));
    const lines = parts[i + 1].split("\n").map(l => l.trim()).filter(l => /[।॥]/.test(l));
    out.push({ n, lines });
  }
  return out;
}
export function chak() {
  const t = fixChak(fs.readFileSync(new URL("hp_chakradeo.txt", RAW), "utf8"));
  return t;
}
if (process.argv[1].endsWith("haripath.mjs")) {
  const m = mahiti(); console.log("mahiti haripath abhangs:", m.length, m.map(x => x.n).join(","), "line counts", m.map(x => x.lines.length).join(","));
  console.log(m[27]?.lines.join("\n"));
  const c = chak(); const i = c.indexOf("श्रीज्ञानेश्वरमहाराजकृत हरिपाठाचे अभंग", c.indexOf("श्री गणेशाय")); console.log(c.slice(c.indexOf("श्री गणेशाय")-50, c.indexOf("श्री गणेशाय")+1800));
}
