# ====================================================================
# STEP 1: Create and push git tags (with 15s delay between each)
# ====================================================================
$tags = @(
  "backoffice-bff-0.5.1",
  "backoffice-ui-0.4.1",
  "cart-0.5.1",
  "customer-0.4.1",
  "inventory-0.4.1",
  "location-0.4.1",
  "media-0.4.1",
  "order-0.5.1",
  "payment-paypal-0.5.1",
  "payment-0.9.1",
  "product-0.4.1",
  "promotion-0.4.1",
  "rating-0.4.1",
  "recommendation-0.4.1",
  "sampledata-0.5.1",
  "search-0.4.1",
  "storefront-bff-0.4.1",
  "storefront-ui-0.4.1",
  "tax-0.4.1",
  "webhook-0.4.1"
)

Write-Host "Creating and pushing $($tags.Count) tags with 15s delay..."
foreach ($tag in $tags) {
  Write-Host "  -> git tag $tag"
  git tag $tag
  Write-Host "  -> git push origin $tag"
  git push origin $tag
  Start-Sleep -Seconds 15
}
Write-Host "All tags pushed. Wait for CI to finish building images."

# ====================================================================
# STEP 2: Update values files (run AFTER all Docker images are built)
# ====================================================================
Write-Host "Updating staging/production values files..."

$updates = @(
  @("backoffice-bff", "0.5.0", "0.5.1"),
  @("backoffice-ui",  "0.4.0", "0.4.1"),
  @("cart",           "0.5.0", "0.5.1"),
  @("customer",       "0.4.0", "0.4.1"),
  @("inventory",      "0.4.0", "0.4.1"),
  @("location",       "0.4.0", "0.4.1"),
  @("media",          "0.4.0", "0.4.1"),
  @("order",          "0.5.0", "0.5.1"),
  @("payment-paypal", "0.5.0", "0.5.1"),
  @("payment",        "0.9.0", "0.9.1"),
  @("product",        "0.4.0", "0.4.1"),
  @("promotion",      "0.4.0", "0.4.1"),
  @("rating",         "0.4.0", "0.4.1"),
  @("recommendation", "0.4.0", "0.4.1"),
  @("sampledata",     "0.5.0", "0.5.1"),
  @("search",         "0.4.0", "0.4.1"),
  @("storefront-bff", "0.4.0", "0.4.1"),
  @("storefront-ui",  "0.4.0", "0.4.1"),
  @("tax",            "0.4.0", "0.4.1"),
  @("webhook",        "0.4.0", "0.4.1")
)

foreach ($u in $updates) {
  $svc  = $u[0]
  $old  = $u[1]
  $new  = $u[2]
  $stg  = "k8s/env/staging/$svc-values.yaml"
  $prd  = "k8s/env/production/$svc-values.yaml"

  if (Test-Path $stg) {
    (Get-Content $stg) -replace "tag: $old", "tag: $new" | Set-Content $stg
    Write-Host "  Updated $stg : $old -> $new"
  }
  if (Test-Path $prd) {
    (Get-Content $prd) -replace "tag: $old", "tag: $new" | Set-Content $prd
    Write-Host "  Updated $prd : $old -> $new"
  }
}

#Write-Host "Committing and pushing..."
#git add k8s/env/staging/*-values.yaml k8s/env/production/*-values.yaml
#git commit -m "chore: bump all services to patch version [skip-ci]"
#git push

#Write-Host "Done! ArgoCD will auto-sync the new images."
