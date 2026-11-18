package com.example.dualsimtiles;
import org.junit.Test;
import static org.junit.Assert.*;

public class TokenAuthTest {
    private final String token="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    @Test public void missingAndUnconfiguredTokensAreDenied() {
        assertFalse(TokenAuth.matches(token,null)); assertFalse(TokenAuth.matches("",""));
        assertFalse(TokenAuth.matches(null,token));
    }
    @Test public void onlyExactSecretMatches() {
        assertTrue(TokenAuth.matches(token,token));
        assertFalse(TokenAuth.matches(token,token.toUpperCase(java.util.Locale.ROOT)));
        assertFalse(TokenAuth.matches(token,token.substring(1)));
    }
}
