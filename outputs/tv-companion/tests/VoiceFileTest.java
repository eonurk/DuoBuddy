package family.tv.companion;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;

public final class VoiceFileTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static String sha(byte[] bytes)throws Exception{StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format("%02x",b&255));return hash.toString();}
    public static void main(String[] args)throws Exception{
        File file=new File(args[0],"model.onnx");byte[] old={1,2,3},next={4,5,6,7};Files.write(file.toPath(),old);
        try{VoiceFile.install(new ByteArrayInputStream(next),file,next.length,sha(old),(n,total)->{});throw new AssertionError("corrupt model accepted");}catch(IOException expected){}
        check(java.util.Arrays.equals(Files.readAllBytes(file.toPath()),old),"checksum failure must preserve installed model");
        check(!new File(args[0],"model.onnx.part").exists(),"checksum failure removes temporary file");
        try{VoiceFile.install(new ByteArrayInputStream(next),file,next.length+1,sha(next),(n,total)->{});throw new AssertionError("truncated model accepted");}catch(IOException expected){}
        try{VoiceFile.install(new ByteArrayInputStream(next),file,next.length,sha(next),(n,total)->{if(n>0)throw new IOException("cancelled");});throw new AssertionError("cancelled model accepted");}catch(IOException expected){}
        check(java.util.Arrays.equals(Files.readAllBytes(file.toPath()),old),"cancelled download must preserve selected voice");
        VoiceFile.install(new ByteArrayInputStream(next),file,next.length,sha(next),(n,total)->{});
        check(java.util.Arrays.equals(Files.readAllBytes(file.toPath()),next),"verified model installs atomically");
        check(!new File(args[0],"model.onnx.part").exists(),"successful install leaves no temporary copy");
        System.out.println("Voice download regression checks passed.");
    }
}
