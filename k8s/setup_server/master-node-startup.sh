#!/bin/bash

apt-get update -y
apt-get install -y curl sudo

curl -sfL https://get.k3s.io | K3S_KUBECONFIG_MODE="644" sh -

sleep 20

export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
echo "export KUBECONFIG=/etc/rancher/k3s/k3s.yaml" >> /root/.bashrc

kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

sleep 10

kubectl patch svc argocd-server -n argocd -p '{"spec": {"type": "NodePort", "ports": [{"name": "https", "nodePort": 30443, "port": 443, "targetPort": 8080}]}}'