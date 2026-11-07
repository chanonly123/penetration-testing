// Day 1 afternoon — attack YOUR OWN app to learn what instrumentation sees.
// Android/Java equivalent of the iOS hook.js in iOS_Security_Plan.md.
//
// Run:  frida -U -f com.you.secretvault -l hook.js
// (rooted emulator or device with frida-server running)

Java.perform(function () {
    var Vault = Java.use("com.you.secretvault.SecretVault");

    // Force root check to say "clean" (flip the boolean).
    Vault.isRooted.implementation = function () {
        console.log("[*] isRooted() called -> forcing false");
        return false;
    };

    // Force license valid.
    Vault.isLicenseValid.implementation = function () {
        console.log("[*] isLicenseValid() called -> forcing true");
        return true;
    };

    // Steal / bypass the PIN gate: log the argument, return the secret
    // regardless of what PIN was typed.
    Vault.revealSecret.implementation = function (pin) {
        console.log("[*] revealSecret(pin=" + pin + ")");
        var real = this.revealSecret(pin);      // call original
        console.log("[*] original returned: " + real);
        return "FLAG{replace_me}";               // force-unlock
    };

    console.log("[*] hooks installed");
});

// WHY each check was easy (Day 1 deliverable notes):
//  - single point of failure: one boolean return to flip
//  - predictable symbol name: SecretVault.isRooted, exact match
//  - boolean return: trivially replace false<->true
//  - decision made in-process: app trusts its own result on the device
