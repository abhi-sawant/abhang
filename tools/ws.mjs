// usage: node ws.mjs search <query> | node ws.mjs prefix <prefix> | node ws.mjs cat <category> | node ws.mjs text <title>
const API = "https://mr.wikisource.org/w/api.php";
const [,, mode, ...rest] = process.argv;
const arg = rest.join(" ");
const q = async (params) => {
  const u = API + "?" + new URLSearchParams({ format: "json", formatversion: "2", ...params });
  const r = await fetch(u, { headers: { "User-Agent": "abhang-app-content-research/1.0" } });
  return r.json();
};
if (mode === "search") {
  const j = await q({ action: "query", list: "search", srsearch: arg, srlimit: "30" });
  console.log(j.query.searchinfo, j.query.search.map(s => s.title).join("\n"));
} else if (mode === "prefix") {
  const j = await q({ action: "query", list: "allpages", apprefix: arg, aplimit: "100" });
  console.log(j.query.allpages.map(s => s.title).join("\n"));
} else if (mode === "cat") {
  const j = await q({ action: "query", list: "categorymembers", cmtitle: "वर्ग:" + arg, cmlimit: "200" });
  console.log((j.query?.categorymembers ?? []).map(s => s.title).join("\n"));
} else if (mode === "text") {
  const j = await q({ action: "query", prop: "revisions", rvprop: "content", rvslots: "main", titles: arg });
  console.log(j.query.pages[0].revisions?.[0].slots.main.content ?? "MISSING");
} else if (mode === "siteinfo") {
  const j = await q({ action: "query", meta: "siteinfo", siprop: "statistics" });
  console.log(JSON.stringify(j.query.statistics));
}
