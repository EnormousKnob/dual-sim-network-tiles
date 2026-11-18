package com.example.dualsimtiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class TokenAuth {
    static boolean matches(String expected,String supplied) {
        return expected!=null && expected.length()>=32 && supplied!=null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8));
    }
}
