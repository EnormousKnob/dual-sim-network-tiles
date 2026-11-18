package com.example.dualsimtiles;

import java.lang.reflect.*;
import java.util.*;

/** Independent implementation: cache exact runtime signatures, never hide an invocation failure. */
final class Reflector {
    private final Map<String, Method> cache = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private String lastSignature="";
    String lastSignature() { return lastSignature; }
    Object call(Object receiver, String name, Object... args) throws Exception {
        String key=receiver.getClass().getName()+"#"+name+Arrays.toString(Arrays.stream(args)
                .map(a->a==null ? null : a.getClass()).toArray());
        Method found=cache.get(key);
        if(missing.contains(key)) throw new NoSuchMethodException(name+"/"+args.length);
        if(found==null) {
            for(Method method:receiver.getClass().getMethods()) {
                if(!method.getName().equals(name) || method.getParameterCount()!=args.length) continue;
                Class<?>[] types=method.getParameterTypes(); boolean match=true;
                for(int i=0;i<args.length;i++) {
                    Class<?> t=types[i];
                    if(t==int.class) t=Integer.class;
                    if(t==long.class) t=Long.class;
                    if(t==boolean.class) t=Boolean.class;
                    if(args[i]==null ? t.isPrimitive() : !t.isInstance(args[i])) match=false;
                }
                if(match) { found=method; found.setAccessible(true); cache.put(key,found); break; }
            }
        }
        if(found==null) { missing.add(key); throw new NoSuchMethodException(name+"/"+args.length); }
        lastSignature=found.getName()+Arrays.toString(found.getParameterTypes());
        try { return found.invoke(receiver,args); }
        catch(InvocationTargetException e) {
            Throwable cause=e.getCause();
            if(cause instanceof Exception) throw (Exception)cause;
            throw e;
        }
    }
    Object variants(Object receiver, String name, Object[]... candidates) throws Exception {
        for(Object[] args:candidates) {
            try { return call(receiver,name,args); }
            catch(NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name+"：无已知兼容签名");
    }
}
