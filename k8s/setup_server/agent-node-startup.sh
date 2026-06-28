#!/bin/bash
# 1. Cập nhật hệ thống Debian và cài đặt curl
apt-get update -y
apt-get install -y curl sudo

# 2. Prerequisite: allow k3s VXLAN (UDP 8472) between all nodes in VPC firewall.
# gcloud compute firewall-rules create k3s-vxlan --allow udp:8472 --source-ranges 10.148.0.0/16

# 3. KHAI BÁO THÔNG TIN KẾT NỐI
MASTER_INTERNAL_IP="10.148.0.10"
K3S_TOKEN="<K3S_TOKEN>"  # Thay bằng token thực tế từ master node

# 3. Chạy lệnh cài đặt K3s Agent (Worker)
curl -sfL https://get.k3s.io | K3S_URL=https://${MASTER_INTERNAL_IP}:6443 \
  K3S_TOKEN=${K3S_TOKEN} \
  K3S_NODE_NAME="dev-worker-01" \
  sh -s - agent --node-label env=dev