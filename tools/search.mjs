import { api } from "./wslib.mjs";
for (const q of process.argv.slice(2)) {
  const j = await api({ action: "query", list: "search", srsearch: q, srlimit: "12", srwhat: "text" });
  console.log("== " + q + " (" + j.query.searchinfo.totalhits + ")\n" + j.query.search.map(s => s.title).join(" | "));
}
