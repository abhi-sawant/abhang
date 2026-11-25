import fs from "node:fs";
import path from "node:path";
const API = "https://mr.wikisource.org/w/api.php";
const RAW = path.join(import.meta.dirname, "..", "content", "raw");
fs.mkdirSync(RAW, { recursive: true });
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
export async function api(params) {
  const u = API + "?" + new URLSearchParams({ format: "json", formatversion: "2", ...params });
  for (let attempt = 0; attempt < 8; attempt++) {
    const r = await fetch(u, { headers: { "User-Agent": "abhang-app-content-research/1.0 (offline Marathi abhang app)" } });
    const t = await r.text();
    try { const j = JSON.parse(t); await sleep(1200); return j; }
    catch { await sleep(15000 * (attempt + 1)); }
  }
  throw new Error("rate-limited: " + u);
}
export async function prefix(p) {
  let out = [], cont = {};
  do {
    const j = await api({ action: "query", list: "allpages", apprefix: p, aplimit: "500", ...cont });
    out.push(...j.query.allpages.map(x => x.title));
    cont = j.continue ?? null;
  } while (cont);
  return out;
}
export async function getText(title) {
  const f = path.join(RAW, encodeURIComponent(title) + ".txt");
  if (fs.existsSync(f)) return fs.readFileSync(f, "utf8");
  const j = await api({ action: "query", prop: "revisions", rvprop: "content", rvslots: "main", titles: title });
  const t = j.query.pages[0].revisions?.[0].slots.main.content ?? "";
  fs.writeFileSync(f, t);
  return t;
}
