$ErrorActionPreference = "Stop"
& "$PSScriptRoot\build.ps1"
$map = if ($args.Count -gt 0) { $args[0] } else { "maps/sample.map" }
& java -cp "$PSScriptRoot\out" build3d.Main $map
