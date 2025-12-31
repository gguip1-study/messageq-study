const out = document.querySelector("#out");
const userIdEl = document.querySelector("#userId");
const couponIdEl = document.querySelector("#couponId");

function write(v) {
  out.textContent = typeof v === "string" ? v : JSON.stringify(v, null, 2);
}

async function post(url, body) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  let parsed = text;
  try { parsed = JSON.parse(text); } catch {}
  return { status: res.status, body: parsed };
}

document.querySelector("#syncBtn").onclick = async () => {
  write("동기 발급 요청 중...");
  const body = { userId: Number(userIdEl.value), couponId: Number(couponIdEl.value) };
  write(await post("/api/coupons/issue-sync", body));
};

document.querySelector("#atomicBtn").onclick = async () => {
  write("동기(원자 UPDATE) 발급 요청 중...");
  const body = { userId: Number(userIdEl.value), couponId: Number(couponIdEl.value) };
  write(await post("/api/coupons/issue-sync-atomic", body));
};

document.querySelector("#asyncBtn").onclick = async () => {
  write("MQ 발급 접수 중...");
  const body = { userId: Number(userIdEl.value), couponId: Number(couponIdEl.value) };

  const accepted = await post("/api/coupons/issue-async", body);
  write(accepted);

  const requestId = accepted?.body?.requestId;
  if (!requestId) return;

  // 폴링
  const start = Date.now();
  const timer = setInterval(async () => {
    const r = await fetch(`/api/coupons/requests/${requestId}`);
    const data = await r.json();

    write({ accepted: accepted.body, polled: data, elapsedMs: Date.now() - start });

    if (data.status !== "PENDING") clearInterval(timer);
  }, 400);
};

