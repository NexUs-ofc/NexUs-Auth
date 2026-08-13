const HISTORICO_MAX = 60;

const METRICAS = {
    cpu:      { label: "CPU (%)",           cor: "#22c55e", historico: [] },
    memory:   { label: "Memória JVM (MB)",  cor: "#3b82f6", historico: [] },
    threads:  { label: "Threads",           cor: "#f59e0b", historico: [] },
    requests: { label: "Requests",          cor: "#ef4444", historico: [] }
};

let metricaSelecionada = "cpu";
let canvas, ctx;

function obterChaveAcesso() {
    let chave = localStorage.getItem("tracing_access_key");
    if (!chave) {
        chave = window.prompt("Informe a chave de acesso (TRACING_ACESS):");
        if (chave) {
            localStorage.setItem("tracing_access_key", chave);
        }
    }
    return chave;
}

function limparChaveAcesso() {
    localStorage.removeItem("tracing_access_key");
}

async function atualizarDashboard(){

    const chave = obterChaveAcesso();
    if (!chave) return;

    let response;
    try {
        response = await fetch("/actuator/prometheus?key=" + encodeURIComponent(chave));
    } catch (erro) {
        console.error("Falha ao buscar métricas:", erro);
        return;
    }

    if (response.status === 401) {
        alert("Chave de acesso inválida. Tente novamente.");
        limparChaveAcesso();
        return;
    }

    if (!response.ok) {
        console.error("Erro ao buscar métricas:", response.status);
        return;
    }

    const texto = await response.text();

    const cpu = extrair(texto,"process_cpu_usage")*100;
    const threads = extrair(texto,"jvm_threads_live_threads");
    const requests = extrair(texto,"http_server_requests_seconds_count");
    const memoria = extrair(texto,"jvm_memory_used_bytes")/1024/1024;

    document.getElementById("cpu").innerHTML = cpu.toFixed(1)+" %";
    document.getElementById("threads").innerHTML = threads;
    document.getElementById("requests").innerHTML = requests;
    document.getElementById("memory").innerHTML = memoria.toFixed(1)+" MB";

    registrarHistorico("cpu", cpu);
    registrarHistorico("threads", threads);
    registrarHistorico("requests", requests);
    registrarHistorico("memory", memoria);

    desenharGrafico();
}

function registrarHistorico(nome, valor){
    const metrica = METRICAS[nome];
    metrica.historico.push(valor);
    if (metrica.historico.length > HISTORICO_MAX) {
        metrica.historico.shift();
    }
}

function extrair(texto,nome){

    const linhas = texto.split("\n");

    for(const linha of linhas){

        if(
            linha.startsWith(nome)
            &&
            !linha.startsWith("#")
        ){

            const partes = linha.trim().split(" ");

            return parseFloat(partes[partes.length-1]);

        }

    }

    return 0;

}

function selecionarMetrica(nome){
    metricaSelecionada = nome;

    document.querySelectorAll(".card[data-metrica]").forEach(card => card.classList.remove("selecionado"));
    const cardAtivo = document.querySelector(`.card[data-metrica="${nome}"]`);
    if (cardAtivo) cardAtivo.classList.add("selecionado");

    document.getElementById("grafico-titulo").innerText = METRICAS[nome].label + " — últimos 5 min";

    desenharGrafico();
}

function desenharGrafico(){
    if (!ctx) return;

    const metrica = METRICAS[metricaSelecionada];
    const dados = metrica.historico;

    const largura = canvas.width;
    const altura = canvas.height;

    ctx.clearRect(0,0,largura,altura);

    ctx.fillStyle = "#c1b0da";
    ctx.fillRect(0,0,largura,altura);

    ctx.strokeStyle = "rgba(255,255,255,0.08)";
    ctx.lineWidth = 1;
    const linhasGrade = 5;
    for (let i=0;i<=linhasGrade;i++){
        const y = (altura/linhasGrade)*i;
        ctx.beginPath();
        ctx.moveTo(0,y);
        ctx.lineTo(largura,y);
        ctx.stroke();
    }

    if (dados.length < 2) return;

    const max = Math.max(...dados, 1);
    const min = Math.min(...dados, 0);
    const escala = (max - min) || 1;
    const passoX = largura / (HISTORICO_MAX - 1);
    const offsetX = passoX * (HISTORICO_MAX - dados.length);

    ctx.beginPath();
    ctx.strokeStyle = metrica.cor;
    ctx.lineWidth = 2;

    dados.forEach((valor, indice) => {
        const x = offsetX + passoX * indice;
        const y = altura - ((valor - min) / escala) * (altura * 0.9) - (altura*0.05);

        if (indice === 0) {
            ctx.moveTo(x,y);
        } else {
            ctx.lineTo(x,y);
        }
    });

    ctx.stroke();

    ctx.lineTo(largura, altura);
    ctx.lineTo(offsetX, altura);
    ctx.closePath();
    ctx.fillStyle = metrica.cor + "22";
    ctx.fill();

    const atual = dados[dados.length-1];
    ctx.fillStyle = "#ffffff";
    ctx.font = "bold 14px Arial";
    ctx.fillText(atual.toFixed(1), largura - 60, 20);
}

function iniciarCanvas(){
    canvas = document.getElementById("grafico-canvas");
    ctx = canvas.getContext("2d");

    document.querySelectorAll(".card[data-metrica]").forEach(card => {
        card.style.cursor = "pointer";
        card.addEventListener("click", () => selecionarMetrica(card.dataset.metrica));
    });

    selecionarMetrica("cpu");
}

iniciarCanvas();
atualizarDashboard();
setInterval(atualizarDashboard,5000);