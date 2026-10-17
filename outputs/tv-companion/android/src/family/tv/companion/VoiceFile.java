package family.tv.companion;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

/** A failed, cancelled or corrupt download never becomes an installed model. */
final class VoiceFile {
    interface Progress {void update(long bytes,long total)throws IOException;}
    static void install(InputStream source,File destination,long size,String sha,Progress progress)throws Exception{
        File parent=destination.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create voice storage");
        File part=new File(parent,destination.getName()+".part");boolean installed=false;
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;
            try(OutputStream out=new FileOutputStream(part)){
                byte[] buffer=new byte[65536];int n;progress.update(0,size);
                while((n=source.read(buffer))!=-1){count+=n;if(count>size)throw new IOException("Voice download is larger than expected");progress.update(count,size);digest.update(buffer,0,n);out.write(buffer,0,n);}
            }
            if(count!=size)throw new IOException("Voice download was incomplete");
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!sha.equals(hash.toString()))throw new IOException("Voice checksum did not match. Please try again.");
            progress.update(count,size);
            Files.move(part.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);installed=true;
        }finally{if(!installed)part.delete();}
    }
}
