# ========== 1. Tạo & push tags mới ==========
K8S_SERVICES="product customer cart inventory location media tax promotion rating search recommendation sampledata order payment payment-paypal webhook storefront-bff backoffice-bff storefront-ui backoffice-ui"

git tag -l '*.*.*' | sed 's/^\(.*\)-\([0-9]*\)\.\([0-9]*\)\.\([0-9]*\)/\1 \2 \3 \4/' | sort -u -k1,1 -k2,2 -k3,3 -k4,4 | while read svc maj min pat; do
  # Skip non-k8s services
  echo " $K8S_SERVICES " | grep -q " $svc " || continue

  new_min=$((min + 1))
  new_tag="${svc}-${maj}.${new_min}.${pat}"

  if ! git tag -l "$new_tag" | grep -q .; then
    git tag "$new_tag"
    echo "Created: $new_tag"
    git push origin "$new_tag"
    echo "Pushed. Waiting 4s..."
    sleep 4
  fi
done



# ========== 2. Update staging values ==========
for f in k8s/env/staging/*-values.yaml; do
  svc=$(basename "$f" -values.yaml)
  latest=$(git tag -l "${svc}-*" | sort -V | tail -1)
  ver=$(echo "$latest" | sed "s/^${svc}-//")
  [ -n "$ver" ] && sed -i "s/tag: latest/tag: ${ver}/" "$f" && echo "staging/$svc → $ver"
done

# ========== 3. Update production values ==========
for f in k8s/env/production/*-values.yaml; do
  svc=$(basename "$f" -values.yaml)
  latest=$(git tag -l "${svc}-*" | sort -V | tail -1)
  ver=$(echo "$latest" | sed "s/^${svc}-//")
  [ -n "$ver" ] && sed -i "s/tag: latest/tag: ${ver}/" "$f" && echo "production/$svc → $ver"
done

# ========== 4. Commit & push ==========
git add k8s/env/staging k8s/env/production
git commit -m "release: bump staging/production image tags"
git push origin main