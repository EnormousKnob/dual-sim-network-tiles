package com.example.dualsimtiles;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReflectorTest {
    public static class FourParameterPhone {
        int calls;
        public boolean write(int sub,int reason,long mask,String pkg) { calls++; return pkg.equals("com.android.shell") && mask==123L; }
    }
    public static class DenyingPhone {
        public boolean write(int sub,int reason,long mask) { throw new SecurityException("denied"); }
        public boolean write(int sub,int reason,long mask,String pkg) { fail("Permission denial must not try another overload"); return true; }
    }
    @Test public void findsFourParameterOemSignatureAndReusesIt() throws Exception {
        Reflector r=new Reflector(); FourParameterPhone phone=new FourParameterPhone();
        for(int i=0;i<2;i++) assertEquals(true,r.variants(phone,"write",new Object[]{8,0,123L},new Object[]{8,0,123L,"com.android.shell"}));
        assertEquals(2,phone.calls);
    }
    @Test(expected=SecurityException.class) public void unwrapsPermissionErrorWithoutFallback() throws Exception {
        new Reflector().variants(new DenyingPhone(),"write",new Object[]{8,0,123L},new Object[]{8,0,123L,"com.android.shell"});
    }
    @Test(expected=NoSuchMethodException.class) public void wrongMaskTypeCannotSelectOverload() throws Exception {
        new Reflector().call(new FourParameterPhone(),"write",8,0,123,"com.android.shell");
    }
}
