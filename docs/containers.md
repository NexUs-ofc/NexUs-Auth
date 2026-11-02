# Containers e Kubernetes

## Padrão comum

- `Dockerfile` na raiz; build e runtime separados quando há compilação/dependências.
- Processo sem root; credenciais fora da imagem e da configuração versionada.
- `k8s/kustomization.yaml`, namespace `nexus`, imagem `nexus-auth:local` substituível por qualquer registry.
- Sem conta cloud, registry ou integração de autenticação de um provedor específico.
- A infraestrutura é uma alternativa portátil. Não substitui as hospedagens atuais em Discloud, Render e Vercel.
- A CI compila a imagem e renderiza os manifests; não publica imagens nem executa deploys.

## Imagem local

Execute os comandos a partir da raiz deste repositório, com Docker Engine em execução:

```sh
docker build -t nexus-auth:local .
docker run --rm --env-file .env -p 8080:8080 nexus-auth:local
```

## Configuração e credenciais

Prepare um arquivo `.env` local a partir das variáveis documentadas no README e em `.env.example` (quando existir). Ele nunca deve entrar na imagem nem no Git. Bancos e serviços externos precisam ser alcançáveis a partir do cluster; `localhost` dentro do Pod é o próprio container.

```sh
kubectl apply -f k8s/namespace.yaml
kubectl create secret generic nexus-auth-secret --namespace nexus --from-env-file=.env --dry-run=client -o yaml | kubectl apply -f -
```

O Secret é fornecido externamente, sem valores fictícios versionados. Atualizações exigem reiniciar o workload para recarregar variáveis de ambiente.

## Kubernetes em qualquer cluster

É necessário um cluster configurado no seu kubeconfig. Para usar registry remoto:

1. Compile e publique a imagem em um registry sob seu controle.
2. Dentro de `k8s/`, execute `kubectl kustomize` após ajustar `images` no `kustomization.yaml` (ou use `kustomize edit set image nexus-auth=REGISTRY/PROJETO/nexus-auth:TAG`). `REGISTRY`, `PROJETO` e `TAG` são substituições obrigatórias para esse exemplo.
3. Em registry privado, configure `imagePullSecrets` no cluster. Não é necessário um registry específico.

Para Kind/Minikube, carregue `nexus-auth:local` nos nós usando `kind load docker-image` ou `minikube image load`. A política `IfNotPresent` permite a imagem local.

```sh
kubectl kustomize k8s
kubectl apply -k k8s
kubectl rollout status deployment/nexus-auth -n nexus --timeout=5m
kubectl port-forward service/nexus-auth 8080:80 -n nexus
```

O Service é `ClusterIP`: o acesso local é por port-forward. Para produção, configure Ingress/Gateway e TLS conforme o cluster. A configuração não presume LoadBalancer de cloud.

Os probes verificam a inicialização e disponibilidade HTTP do processo, não o funcionamento integral das integrações externas. Ajuste limites de CPU/memória a partir de medições reais.

O Pod habilita os probes do Spring Boot com `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true`, disponibilizando os grupos liveness/readiness existentes do Actuator.

`Dockerfile.vercel` é mantido como alias de compatibilidade para integrações já configuradas. Use `Dockerfile` em novas integrações; ambos devem permanecer equivalentes.

## Referências

- https://docs.docker.com/build/building/multi-stage/
- https://kubernetes.io/docs/tasks/manage-kubernetes-objects/kustomization/
