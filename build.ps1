$ErrorActionPreference = "Stop"
$src = Get-ChildItem -Path "$PSScriptRoot\src\build3d" -Filter "*.java" | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force -Path "$PSScriptRoot\out" | Out-Null
& javac -d "$PSScriptRoot\out" -encoding UTF-8 $src
Write-Host "Build OK -> out\"
