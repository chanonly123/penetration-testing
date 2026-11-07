# 2-Day iOS Security Prep: Build, Attack, Harden

**Goal:** Build a small iOS app, learn how instrumentation tools see it, then iteratively add and harden detections — the same loop a Runtime Protection Agent SDK performs. You attack your *own* test app to understand what your detections must catch. This mirrors OWASP MASTG and the UnCrackable apps.

**Maps to:** MASVS-RESILIENCE-1 (platform integrity / jailbreak detection), RESILIENCE-2 (anti-tampering), RESILIENCE-4 (anti-dynamic-analysis / anti-debug & anti-hooking), plus RESILIENCE-3 (anti-static-analysis) in the Ghidra round.

---

## Setup (before Day 1, ~1 hour)

You need Frida running against the app, which needs a **jailbroken iOS device** (or jailbroken simulator). Options, easiest first:

- A spare device on a jailbreakable iOS version (palera1n / checkra1n-era hardware is simplest).
- **No jailbroken device?** Build the same test app on **Android** and use a rooted emulator — Frida works out of the box. The role wants cross-platform anyway, so this still counts. Port the *concepts* to iOS after.
- Don't burn a day fighting a jailbreak. If it's not working in ~2 hours, pivot to Android.

**Install:**
```bash
pip install frida-tools objection
# Ghidra: download from https://github.com/NationalSecurityAgency/ghidra/releases (free)
# On the jailbroken device: install frida-server from the Cydia/Sileo repo (https://build.frida.re)
```

**Verify:** `frida-ps -Ua` should list running apps on the device.

---

## Day 1 — Build the target and watch it fall

### Morning: build a deliberately weak app (~2–3 hrs)

A single screen. Keep security logic in plain Swift with obvious names — you *want* it weak so you can see it break.

```swift
// SecretVault.swift
struct SecretVault {
    // Weak on purpose: hardcoded secret, boolean gate, obvious names.
    func isLicenseValid() -> Bool {
        return true                      // Round 3 will remove this single point of failure
    }

    func isJailbroken() -> Bool {
        let paths = ["/Applications/Cydia.app",
                     "/usr/sbin/sshd",
                     "/bin/bash"]
        return paths.contains { FileManager.default.fileExists(atPath: $0) }
    }

    func revealSecret(pin: String) -> String? {
        guard pin == "1337" else { return nil }
        return "FLAG{replace_me}"
    }
}
```

In the view: on launch, if `isJailbroken()` show "device not trusted" and disable the UI. On correct PIN + `isLicenseValid()`, show the secret string on screen.

### Afternoon: observe it with Frida on your own app (~2–3 hrs)

Everything here targets YOUR app, to learn what instrumentation can see.

**List classes / trace your own methods:**
```bash
frida-trace -U -f com.you.secretvault -m "-[SecretVault *]"
```

**Flip your own return values and watch the app misbehave:**
```javascript
// hook.js — run: frida -U -f com.you.secretvault -l hook.js
if (ObjC.available) {
  // Force jailbreak check to say "clean"
  Interceptor.attach(ObjC.classes.SecretVault['- isJailbroken'].implementation, {
    onLeave: function (ret) { ret.replace(0x0); }
  });
  // Force license valid
  Interceptor.attach(ObjC.classes.SecretVault['- isLicenseValid'].implementation, {
    onLeave: function (ret) { ret.replace(0x1); }
  });
  console.log("[*] hooks installed");
}
```

**Record, for each check, WHY it was easy:**
- Single point of failure (one return value)
- Predictable symbol name
- Boolean return (flip 0x0 ↔ 0x1)
- Decision made in-process, trusting the device

> **Day 1 deliverable:** a notes file "attack surface of a naive app." That's half an interview answer.

---

## Day 2 — Harden iteratively, re-test, write it up

One round at a time. After each, re-run Frida and record what changed and what still bypasses it.

### Round 1 — anti-debugging (~1 hr)

```c
#include <sys/ptrace.h>
#include <sys/sysctl.h>
#include <unistd.h>

void deny_debugger(void) {
    ptrace(PT_DENY_ATTACH, 0, 0, 0);     // stops debugger attach
}

bool is_being_traced(void) {
    int mib[4] = { CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid() };
    struct kinfo_proc info;
    size_t size = sizeof(info);
    sysctl(mib, 4, &info, &size, NULL, 0);
    return (info.kp_proc.p_flag & P_TRACED) != 0;
}
```
**Re-test & note the limit:** a hook on `ptrace`/`sysctl` itself bypasses this. Can't stand alone.

### Round 2 — instrumentation detection (~1.5 hrs)

```c
#include <mach-o/dyld.h>
#include <string.h>

bool suspicious_dylib_loaded(void) {
    uint32_t count = _dyld_image_count();
    const char *bad[] = { "FridaGadget", "frida", "cynject", "libcycript", "SubstrateLoader" };
    for (uint32_t i = 0; i < count; i++) {
        const char *name = _dyld_get_image_name(i);
        for (int j = 0; j < 5; j++)
            if (strstr(name, bad[j])) return true;
    }
    return false;
}
```
Also: probe the default Frida port (27042) and scan thread names for `gum-js-loop`.
**Re-test & note:** a renamed gadget evades name checks. Detection must be **layered**, not a single signature.

### Round 3 — remove single points of failure (~1.5 hrs)

The most important round. Stop returning a bare boolean — fold the check into the thing it protects, so tampering corrupts output instead of flipping a flag.

```swift
// Instead of: if isLicenseValid() { showSecret() }
// Derive the decryption key from integrity state. Tampering => wrong key => garbage.
func secretKey() -> Data {
    var seed = baseSeed()
    if is_being_traced()        { seed ^= 0xA5 }   // silently corrupts, no branch to hook
    if suspicious_dylib_loaded() { seed ^= 0x3C }
    return deriveKey(seed)
}
```
Move at least one check into a **C function** (harder to spot, and it's your C/interop talking point for the JD).
**Re-test:** the easy "flip the return" hook no longer helps — there's no boolean to flip.

### Round 4 — static analysis with Ghidra (~1.5 hrs)

- Open YOUR compiled binary in Ghidra. Find your check functions in the disassembly.
- See how readable symbol names + plaintext strings ("device not trusted") lead an analyst straight to the logic.
- Harden:
  - Strip symbols: `strip`, and set `Deployment Postprocessing` / `Strip Debug Symbols` = Yes.
  - Encrypt/obfuscate giveaway strings (don't ship `"/Applications/Cydia.app"` in cleartext — XOR or assemble at runtime).
  - Confirm in Ghidra the obvious landmarks are gone.

### Final hour — write it up

One page: each weakness → hardening → what still bypasses it → defense-in-depth conclusion. Publish on Medium like your other posts → turns 2 days into a portfolio piece.

---

## The through-line for the interview

Every round teaches the point the role is built on:

> **Client-side resilience raises attacker cost; it never reaches zero.**

- If asked *"how would you defeat your own SDK?"* — your honest, tested answer (hook the detector, rename the gadget, patch the binary) reads as competence, not weakness.
- Resilience is **defense in depth**, never a substitute for STORAGE / CRYPTO / NETWORK / AUTH being correct.
- Map everything you built to **MASVS-RESILIENCE-1, -2, -4** (and **-3** for the Ghidra round) when you describe it.

---

## Quick reference — APIs you'll name

| Purpose | API / technique |
|---|---|
| Anti-debug (block attach) | `ptrace(PT_DENY_ATTACH, 0, 0, 0)` |
| Detect debugger | `sysctl` + `KERN_PROC_PID` → `P_TRACED` flag |
| Detect injected dylibs | `_dyld_image_count()`, `_dyld_get_image_name()` |
| Detect Frida | port 27042 probe, thread name `gum-js-loop`, gadget in image list |
| Jailbreak signals | suspicious paths, write outside sandbox, `fork()` success, `cydia://` URL scheme, symlink checks |
| Integrity | code signature check, Mach-O load commands for injected dylibs, `__TEXT` checksum |
| Attacker tools to know | Frida, Objection, Ghidra, LLDB, `otool`, `nm`, `codesign`, `class-dump` |

## Crackme practice (if you finish early)

OWASP's **UnCrackable iOS Level 1 & 2** — small apps with jailbreak detection + anti-debug + a hidden secret. Solving them gives a precise interview story: the detection used, how you defeated it, what you'd do to make it harder. Part of MASTG.
