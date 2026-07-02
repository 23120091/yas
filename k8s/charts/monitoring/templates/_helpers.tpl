{{/* Tên cơ sở cho chart */}}
{{- define "monitoring.fullname" -}}
{{- printf "%s-%s" .Release.Name .Chart.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Tên service Prometheus */}}
{{- define "prometheus.serviceName" -}}
{{- printf "%s-prometheus-server" .Release.Name }}
{{- end }}

{{/* Tên service Grafana */}}
{{- define "grafana.serviceName" -}}
{{- printf "%s-grafana" .Release.Name }}
{{- end }}
