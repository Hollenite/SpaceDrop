# SpaceDrop Windows Tray



.NET 8 **WinForms tray app** (no WinUI/XAML) — PC companion for SpaceDrop. Replaces `spacedrop_pc.py` for normal use.



## Requirements



- Windows 10 1809+ (build 17763)

- **Visual Studio 2022** with the **.NET desktop development** workload (WinUI / Windows App SDK **not** required)

- **.NET 8 SDK** (installed with VS or from https://dotnet.microsoft.com/download)

- Bluetooth adapter optional — mDNS + TCP still work if BLE peripheral role is unsupported



## Open in Visual Studio 2022



1. **File → Open → Project/Solution**

2. Open `D:\PROJECTS\SpaceDrop\windows\SpaceDrop.sln`

3. Top toolbar: **Release** | **x64**

4. **Build → Build Solution** (`Ctrl+Shift+B`)

5. **Debug → Start Without Debugging** (`Ctrl+F5`)



The app has **no main window** — look for the tray icon near the clock (^ hidden icons).



## Command-line build



```powershell

cd D:\PROJECTS\SpaceDrop\windows\SpaceDrop.Tray

dotnet restore

dotnet build -c Release

dotnet run -c Release --no-build

```



Output exe:



`bin\x64\Release\net8.0-windows10.0.19041.0\SpaceDrop.Tray.exe`



## NuGet restore



`windows\NuGet.config` points restore at **nuget.org**. If packages fail to restore in VS:



1. **Tools → NuGet Package Manager → Package Manager Settings → Package Sources**

2. Ensure **nuget.org** is enabled

3. Right-click solution → **Restore NuGet Packages**



## If build fails with CS2012 / intermediate XAML errors



That was caused by the earlier WinUI setup. This project **no longer uses WinUI or XAML**. If you still see stale errors:



1. Close Visual Studio

2. Delete `windows\SpaceDrop.Tray\bin` and `windows\SpaceDrop.Tray\obj`

3. Reopen the solution and rebuild **Release | x64**



## Runtime paths



| Item | Path |

|---|---|

| Received files | `%USERPROFILE%\SpaceDrop\received\` |

| Device ID | `%LOCALAPPDATA%\SpaceDrop\device_id.txt` |

| TCP port | **58392** |
| Build output (VS Release\|x64) | `SpaceDrop.Tray\bin\x64\Release\net8.0-windows10.0.19041.0\SpaceDrop.Tray.exe` |



## Firewall



Allow inbound **TCP 58392** if Android cannot connect (same as the Python script).



## Dev fallback



`spacedrop_pc.py` in the repo root remains available for quick Python-only testing. Do not run both at once — they share port 58392.



## Troubleshooting



| Symptom | Fix |

|---|---|

| Packages only from "Offline Packages" | Check `windows\NuGet.config`; enable nuget.org in VS |

| No tray icon | Expand hidden icons (^) in taskbar |

| BLE off in status | Normal on some PCs; mDNS transfer still works |

| Transfer fails | Windows Firewall → allow port 58392 |

