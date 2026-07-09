$Image = "thanhthong2005/debezium-connect-postgresql:1.1.0-kafka-4.3.0-debezium-1"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

Set-Location $ScriptDir

docker build -t $Image .
docker push $Image

Write-Host "`nPushed: $Image`n"
Write-Host "Update values.yaml:"
Write-Host "  image: $Image"
