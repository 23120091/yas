$Image = "thanhthong2005/debezium-connect-postgresql:2.7.3.Final"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

Set-Location $ScriptDir

docker build -t $Image .
docker push $Image

Write-Host "`nPushed: $Image`n"
Write-Host "Update values.yaml:"
Write-Host "  image: $Image"
