{{/*
Expand the name of the chart.
*/}}
{{- define "dwc-dp-analyser.name" -}}
{{- .Chart.Name }}
{{- end }}

{{/*
Create a default fully qualified app name using the release name.
*/}}
{{- define "dwc-dp-analyser.fullname" -}}
{{- .Release.Name }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "dwc-dp-analyser.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{ include "dwc-dp-analyser.selectorLabels" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "dwc-dp-analyser.selectorLabels" -}}
app.kubernetes.io/name: {{ include "dwc-dp-analyser.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
