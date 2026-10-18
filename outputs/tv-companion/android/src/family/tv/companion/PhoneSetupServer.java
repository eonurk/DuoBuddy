package family.tv.companion;

import android.content.Context;
import android.security.keystore.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.security.*;
import java.security.cert.X509Certificate;
import java.math.BigInteger;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Short-lived, TV-confirmed HTTPS credential setup. No plaintext listener or general remote API. */
final class PhoneSetupServer implements AutoCloseable {
    interface Approval {boolean confirm(String provider,String model)throws Exception;}
    private static final String ALIAS="duobuddy-local-setup-tls-v2";
    private final Context context;private final Approval approval;private final Runnable connected;private final ExecutorService clients=Executors.newFixedThreadPool(2);
    private final String token=UUID.randomUUID().toString().replace("-","");private final AtomicBoolean saving=new AtomicBoolean();
    private final long expires=android.os.SystemClock.elapsedRealtime()+180000;
    private volatile boolean closed,used;private SSLServerSocket server;
    final String provider,model;private String fingerprint;
    PhoneSetupServer(Context c,String provider,String model,Approval approval,Runnable connected){context=c;this.provider=provider;this.model=model;this.approval=approval;this.connected=connected;}
    String start()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyPairGenerator generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA384,KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=DuoBuddy TV local setup"))
                .setCertificateSerialNumber(new BigInteger(120,new SecureRandom()))
                .setCertificateNotBefore(new Date(System.currentTimeMillis()-86400000L))
                .setCertificateNotAfter(new Date(System.currentTimeMillis()+5*365*86400000L)).build());generator.generateKeyPair();
        }
        PrivateKey key=(PrivateKey)store.getKey(ALIAS,null);java.security.cert.Certificate[] raw=store.getCertificateChain(ALIAS);
        X509Certificate[] chain=new X509Certificate[raw.length];for(int i=0;i<raw.length;i++)chain[i]=(X509Certificate)raw[i];
        fingerprint=hex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));
        X509KeyManager manager=new X509KeyManager(){
            public String[] getClientAliases(String type,java.security.Principal[] issuers){return null;}
            public String chooseClientAlias(String[] type,java.security.Principal[] issuers,Socket socket){return null;}
            public String[] getServerAliases(String type,java.security.Principal[] issuers){return "RSA".equals(type)?new String[]{ALIAS}:null;}
            public String chooseServerAlias(String type,java.security.Principal[] issuers,Socket socket){return "RSA".equals(type)?ALIAS:null;}
            public X509Certificate[] getCertificateChain(String alias){return ALIAS.equals(alias)?chain:null;}
            public PrivateKey getPrivateKey(String alias){return ALIAS.equals(alias)?key:null;}
        };
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(new KeyManager[]{manager},null,new SecureRandom());
        String host=lanAddress();server=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0,4,InetAddress.getByName(host));server.setEnabledProtocols(new String[]{"TLSv1.2"});server.setSoTimeout(1000);
        Thread listener=new Thread(()->{
            while(!closed&&android.os.SystemClock.elapsedRealtime()<expires){
                try{Socket socket=server.accept();socket.setSoTimeout(15000);clients.execute(()->handle(socket));}catch(SocketTimeoutException ignored){}catch(Exception e){if(!closed)close();}
            }close();
        },"DuoBuddy-key-setup");listener.setDaemon(true);listener.start();
        return "https://"+host+":"+server.getLocalPort()+"/?pair="+token;
    }
    String fingerprint(){return fingerprint;}
    private static String lanAddress()throws Exception{
        Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
        while(interfaces.hasMoreElements()){
            NetworkInterface item=interfaces.nextElement();if(!item.isUp()||item.isLoopback()||item.getName().startsWith("tun"))continue;
            Enumeration<InetAddress> addresses=item.getInetAddresses();while(addresses.hasMoreElements()){InetAddress address=addresses.nextElement();if(address instanceof Inet4Address&&address.isSiteLocalAddress())return address.getHostAddress();}
        }throw new IOException("Connect the TV and phone to the same home network first.");
    }
    private void handle(Socket socket){
        try(Socket client=socket){
            InputStream in=client.getInputStream();ByteArrayOutputStream header=new ByteArrayOutputStream();int next,match=0;
            while((next=in.read())!=-1){header.write(next);if(header.size()>16384)throw new IOException("Request header too large");match=next=="\r\n\r\n".charAt(match)?match+1:next=='\r'?1:0;if(match==4)break;}
            String[] lines=header.toString("US-ASCII").split("\r\n"),first=lines[0].split(" ");if(first.length<2)return;
            URI uri=URI.create(first[1]);Map<String,String> query=form(uri.getRawQuery());
            if(closed||used||android.os.SystemClock.elapsedRealtime()>=expires||!MessageDigest.isEqual(token.getBytes("UTF-8"),query.getOrDefault("pair","").getBytes("UTF-8"))){reply(client,403,"Setup expired","Close and reopen Set up on phone on the TV.",false);return;}
            if(first[0].equals("GET")&&uri.getPath().equals("/")){reply(client,200,"Connect DuoBuddy","Paste your own provider key. Nothing is sent until you submit and confirm on the TV.",true);return;}
            if(!first[0].equals("POST")||!uri.getPath().equals("/save")){reply(client,404,"Not found","",false);return;}
            int length=0;String contentType="";for(String line:lines){if(line.toLowerCase(Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(line.substring(15).trim());if(line.toLowerCase(Locale.ROOT).startsWith("content-type:"))contentType=line.substring(13).trim();}
            if(length<1||length>8192||!contentType.startsWith("application/x-www-form-urlencoded")){reply(client,400,"Invalid setup request","Use the form displayed by this TV.",true);return;}
            byte[] body=new byte[length];for(int at=0;at<length;){int n=in.read(body,at,length-at);if(n<0)throw new EOFException();at+=n;}
            if(!saving.compareAndSet(false,true)){reply(client,409,"Setup in progress","Confirm the pending request on your TV.",false);return;}
            try{
                Map<String,String> values=form(new String(body,"UTF-8"));String[] choice=values.getOrDefault("choice","").split("\\|",2);
                if(choice.length!=2)throw new IllegalArgumentException("Choose a model.");String selected=choice[0],id=values.getOrDefault("custom","").trim();if(id.isEmpty())id=choice[1];AiProtocol.validateSelection(selected,id);
                String secret=values.getOrDefault("key","").trim();
                if(!approval.confirm(selected,id)||closed)throw new IllegalStateException("The TV did not approve this setup request.");
                LocalController.get(context).configure(selected,id,secret);used=true;reply(client,200,"Connected","Your TV is ready. You can close this page; your phone is no longer needed.",false);connected.run();
            }catch(Exception e){reply(client,400,"Could not connect",e.getMessage()==null?"Please try again.":e.getMessage(),true);}
            finally{Arrays.fill(body,(byte)0);saving.set(false);}
        }catch(Exception ignored){/* Never log setup URLs, request bodies, API keys or TLS secrets. */}
    }
    private void reply(Socket client,int status,String title,String message,boolean showForm)throws IOException{
        String selected=provider+"|"+model;
        String options="<option value='openai|gpt-6-luna'"+(selected.equals("openai|gpt-6-luna")?" selected":"")+">OpenAI · GPT-6 Luna</option><option value='gemini|gemini-3.1-flash-lite'"+(provider.equals("gemini")?" selected":"")+">Google · Gemini 3.1 Flash-Lite</option>";
        String form=showForm?"<form method='post' action='/save?pair="+token+"'><label>Model<select name='choice'>"+options+"</select></label><label>API key<input name='key' type='password' autocomplete='off' required spellcheck='false'></label><details><summary>Custom model</summary><input name='custom' placeholder='Optional exact model ID' autocomplete='off' spellcheck='false'></details><button>Connect on my TV</button></form><p>Then confirm the request on the TV. Your key is encrypted on the TV and sent only to your chosen API provider.</p>":"";
        String html="<!doctype html><html lang='en'><meta name='viewport' content='width=device-width,initial-scale=1'><meta charset='utf-8'><title>DuoBuddy setup</title><style>body{font:17px system-ui;background:#f6f7f9;color:#1e293b;max-width:480px;margin:40px auto;padding:24px}h1{font-size:30px}label{display:block;margin:20px 0}input,select,button{box-sizing:border-box;width:100%;font:inherit;padding:14px;border:1px solid #cbd5e1;border-radius:12px;margin-top:8px}button{background:#f97316;color:white;border:0;font-weight:700}p{line-height:1.5}details{margin-bottom:20px}</style><h1>"+escape(title)+"</h1><p>"+escape(message)+"</p>"+form+"</html>";
        byte[] bytes=html.getBytes("UTF-8");OutputStream out=client.getOutputStream();out.write(("HTTP/1.1 "+status+" OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+bytes.length+"\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));out.write(bytes);out.flush();
    }
    private static Map<String,String> form(String encoded)throws Exception{Map<String,String> values=new HashMap<>();if(encoded!=null)for(String pair:encoded.split("&")){String[] item=pair.split("=",2);values.put(URLDecoder.decode(item[0],"UTF-8"),URLDecoder.decode(item.length>1?item[1]:"","UTF-8"));}return values;}
    static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte value:bytes)out.append(String.format(Locale.ROOT,"%02X",value&255));return out.toString();}
    public void close(){closed=true;try{if(server!=null)server.close();}catch(IOException ignored){}clients.shutdownNow();}
}
