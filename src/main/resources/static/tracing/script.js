async function atualizarDashboard(){

    const response = await fetch("/actuator/prometheus");

    const texto = await response.text();

    document.getElementById("cpu").innerHTML =
        (extrair(texto,"process_cpu_usage")*100).toFixed(1)+" %";

    document.getElementById("threads").innerHTML =
        extrair(texto,"jvm_threads_live_threads");

    document.getElementById("requests").innerHTML =
        extrair(texto,"http_server_requests_seconds_count");

    const memoria = extrair(texto,"jvm_memory_used_bytes");

    document.getElementById("memory").innerHTML =
        (memoria/1024/1024).toFixed(1)+" MB";

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

atualizarDashboard();

setInterval(atualizarDashboard,5000);