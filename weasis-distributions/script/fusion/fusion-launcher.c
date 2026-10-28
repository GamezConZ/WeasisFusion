/*
 * Native launcher of the portable Windows package: starts Weasis with the Java runtime bundled next
 * to it (runtime\bin\javaw.exe) and forwards its command line untouched, so DICOM paths,
 * "$dicom:get ..." commands and weasis:// URIs reach org.weasis.launcher.AppLauncher as typed.
 *
 * Build (see build-windows.sh): x86_64-w64-mingw32-gcc -municode -mwindows -O2 -s
 *   -DJVM_OPTIONS='L"..."' -DAPP_TITLE='L"..."' fusion-launcher.c launcher-res.o -o App.exe
 */
#define WIN32_LEAN_AND_MEAN
#include <stdlib.h>
#include <wchar.h>
#include <windows.h>

/* Given by build-windows.sh from fusion-env.sh, so every package starts the JVM the same way. */
#ifndef JVM_OPTIONS
#define JVM_OPTIONS L"--enable-native-access=ALL-UNNAMED"
#endif
#ifndef APP_TITLE
#define APP_TITLE L"Weasis"
#endif

/* The command line after the program name, verbatim, so the caller's quoting is kept. */
static const wchar_t *forwarded_args(const wchar_t *cmd) {
  if (*cmd == L'"') {
    cmd++;
    while (*cmd && *cmd != L'"') {
      cmd++;
    }
    if (*cmd) {
      cmd++;
    }
  } else {
    while (*cmd && *cmd != L' ' && *cmd != L'\t') {
      cmd++;
    }
  }
  while (*cmd == L' ' || *cmd == L'\t') {
    cmd++;
  }
  return cmd;
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR cmdLine, int show) {
  (void)instance;
  (void)previous;
  (void)cmdLine;
  (void)show;

  wchar_t dir[MAX_PATH];
  DWORD n = GetModuleFileNameW(NULL, dir, MAX_PATH);
  if (n == 0 || n >= MAX_PATH) {
    return 1;
  }
  wchar_t *slash = wcsrchr(dir, L'\\');
  if (slash) {
    *slash = L'\0';
  }

  const wchar_t *args = forwarded_args(GetCommandLineW());
  size_t len = wcslen(dir) * 4 + wcslen(JVM_OPTIONS) + wcslen(args) + 512;
  wchar_t *cmd = malloc(len * sizeof(wchar_t));
  if (!cmd) {
    return 1;
  }
  swprintf(cmd, len,
           L"\"%ls\\runtime\\bin\\javaw.exe\" -splash:\"%ls\\resources\\images\\about-round.png\" "
           L"%ls -cp \"%ls\\weasis-launcher.jar;%ls\\felix.jar\" "
           L"org.weasis.launcher.AppLauncher %ls",
           dir, dir, JVM_OPTIONS, dir, dir, args);

  STARTUPINFOW si;
  PROCESS_INFORMATION pi;
  ZeroMemory(&si, sizeof(si));
  si.cb = sizeof(si);
  ZeroMemory(&pi, sizeof(pi));
  if (!CreateProcessW(NULL, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
    MessageBoxW(NULL, L"Cannot start the Java runtime (runtime\\bin\\javaw.exe).", APP_TITLE,
                MB_ICONERROR | MB_OK);
    free(cmd);
    return 1;
  }
  CloseHandle(pi.hThread);
  CloseHandle(pi.hProcess);
  free(cmd);
  return 0;
}
