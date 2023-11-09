# Elastic stream consumers with ZIO
_Scala In The City - 9th November 2023_

## Running the demo
- `minikube start`
- `eval $(minikube -p minikube docker-env)`
- `sbt "k8s-example / docker:publishLocal"`
- `helm upgrade --install -n esc --create-namespace elastic-stream-consumer-demo helm-chart -f helm-chart/values.yaml`

You can then change the number of replicas and visualise in the logs the re-balance: 

- `kubectl -n esc scale deploy elastic-stream-consumer-demo-zio-elastic-stream-consumption --replicas=2`
