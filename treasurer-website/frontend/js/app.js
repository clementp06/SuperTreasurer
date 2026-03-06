const BACKEND_BASE_URL = "/treasurer-api";

const form = document.getElementById("expenseForm");
const statusEl = document.getElementById("status");
const sendBtn = document.getElementById("sendBtn");

const sigCanvas = document.getElementById("sigCanvas");
const clearSigBtn = document.getElementById("clearSig");
const ctx = sigCanvas.getContext("2d");

let drawing = false;
let hasInk = false;

function setStatus(msg) {
  statusEl.textContent = msg;
}

function setBusy(isBusy) {
  sendBtn.disabled = isBusy;
  clearSigBtn.disabled = isBusy;
  const inputs = form.querySelectorAll("input, button");
  inputs.forEach((el) => {
    if (el.id !== "clearSig" && el.id !== "sendBtn") el.disabled = isBusy;
  });
}

function initCanvas() {
  ctx.lineWidth = 3;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";
  ctx.strokeStyle = "#000000";
  clearCanvas();
}

function clearCanvas() {
  ctx.clearRect(0, 0, sigCanvas.width, sigCanvas.height);
  hasInk = false;
}

function getPos(evt) {
  const rect = sigCanvas.getBoundingClientRect();
  const x = (evt.clientX - rect.left) * (sigCanvas.width / rect.width);
  const y = (evt.clientY - rect.top) * (sigCanvas.height / rect.height);
  return { x, y };
}

sigCanvas.addEventListener("pointerdown", (e) => {
  drawing = true;
  sigCanvas.setPointerCapture(e.pointerId);
  const p = getPos(e);
  ctx.beginPath();
  ctx.moveTo(p.x, p.y);
});

sigCanvas.addEventListener("pointermove", (e) => {
  if (!drawing) return;
  const p = getPos(e);
  ctx.lineTo(p.x, p.y);
  ctx.stroke();
  hasInk = true;
});

sigCanvas.addEventListener("pointerup", () => {
  drawing = false;
});

sigCanvas.addEventListener("pointercancel", () => {
  drawing = false;
});

clearSigBtn.addEventListener("click", clearCanvas);

function canvasToPngBlob100() {
  return new Promise((resolve) => {
    const out = document.createElement("canvas");
    out.width = 100;
    out.height = 100;
    const octx = out.getContext("2d");

    octx.clearRect(0, 0, 100, 100);
    octx.drawImage(sigCanvas, 0, 0, 100, 100);

    out.toBlob((blob) => resolve(blob), "image/png");
  });
}

async function postExpenseJson(payload,apiKey) {
  const r = await fetch(`${BACKEND_BASE_URL}/member/expenses`, {
    method: "POST",
    headers: {
      "X-Api-Key": apiKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  const data = await r.json().catch(() => null);
  if (!r.ok) {
    const msg = data && data.error ? data.error : `HTTP ${r.status}`;
    throw new Error(msg);
  }
  return data.id;
}

async function postExpenseFiles(id, signatureBlob, invoiceFile, apiKey) {
  const fd = new FormData();
  fd.append("signature", new File([signatureBlob], "signature.png", { type: "image/png" }));
  fd.append("invoice", invoiceFile);

  const r = await fetch(`${BACKEND_BASE_URL}/member/expenses/${id}/files`, {
    method: "POST",
    headers: {
      "X-Api-Key": apiKey,
    },
    body: fd,
  });

  const data = await r.json().catch(() => null);
  if (!r.ok) {
    const msg = data && data.error ? data.error : `HTTP ${r.status}`;
    throw new Error(msg);
  }
}

form.addEventListener("submit", async (e) => {
  e.preventDefault();

  const invoice = document.getElementById("invoiceFile").files[0];
  if (!invoice) {
    setStatus("⚠️ Ajoute une facture.");
    return;
  }
  if (!hasInk) {
    setStatus("⚠️ Ajoute une signature (un trait suffit).");
    return;
  }
  const apiKey = document.getElementById("memberPassword").value;
  const payload = {
    last_name: document.getElementById("lastName").value.trim(),
    first_name: document.getElementById("firstName").value.trim(),
    date: document.getElementById("purchaseDate").value,
    purpose: document.getElementById("purpose").value.trim(),
    designation: document.getElementById("designation").value.trim(),
    amount: Number(document.getElementById("amount").value),
  };

  try {
    setBusy(true);
    setStatus("Envoi du texte…");
    const id = await postExpenseJson(payload, apiKey);

    setStatus(`Texte OK (id=${id}). Envoi des fichiers…`);
    const sigBlob = await canvasToPngBlob100();
    if (!sigBlob) throw new Error("Impossible de générer la signature PNG.");

    await postExpenseFiles(id, sigBlob, invoice, apiKey);

    setStatus(`✅ Envoyé avec succès.\nID: ${id}`);
    form.reset();
    clearCanvas();
  } catch (err) {
    setStatus(`❌ Erreur: ${err.message || err}`);
  } finally {
    setBusy(false);
  }
});

initCanvas();