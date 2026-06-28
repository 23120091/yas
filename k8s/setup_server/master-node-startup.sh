#!/bin/bash

apt-get update -y
apt-get install -y curl sudo

# Prerequisite: Allow k3s VXLAN (UDP 8472) and pod CIDR (10.42.0.0/16) between all nodes.
# On GCP, run this once: https://console.cloud.google.com/networking/firewalls
# Or via gcloud:
#   gcloud compute firewall-rules create k3s-vxlan --allow udp:8472 --source-ranges 10.148.0.0/16

curl -sfL https://get.k3s.io | K3S_KUBECONFIG_MODE="644" sh -

sleep 20

export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
echo "export KUBECONFIG=/etc/rancher/k3s/k3s.yaml" >> /root/.bashrc

kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml

sleep 10

kubectl patch svc argocd-server -n argocd -p '{"spec": {"type": "NodePort", "ports": [{"name": "https", "nodePort": 30443, "port": 443, "targetPort": 8080}]}}'