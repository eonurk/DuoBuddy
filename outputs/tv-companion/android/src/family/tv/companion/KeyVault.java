package family.tv.companion;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Only encrypted provider secrets reach app-private preferences; the wrapping key is non-exportable. */
final class KeyVault {
    private final SharedPreferences prefs;
    private static final String ALIAS="duobuddy-provider-secrets-v1";
    KeyVault(Context c){prefs=c.getSharedPreferences("provider-secrets",Context.MODE_PRIVATE);}
    private synchronized javax.crypto.SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());generator.generateKey();
        }
        return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
    }
    synchronized void save(String provider,String secret)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        cipher.updateAAD(provider.getBytes("UTF-8"));byte[] encrypted=cipher.doFinal(secret.getBytes("UTF-8"));
        String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
        if(!prefs.edit().putString(provider,value).commit())throw new IllegalStateException("Could not save the API key.");
    }
    synchronized String read(String provider)throws Exception{
        String value=prefs.getString(provider,"");if(value.isEmpty())return "";
        String[] parts=value.split(":",2);if(parts.length!=2)throw new IllegalStateException("Saved API key is unreadable. Please set it up again.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));cipher.updateAAD(provider.getBytes("UTF-8"));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),"UTF-8");
    }
    boolean has(String provider){return prefs.contains(provider);}
    synchronized void remove(String provider){prefs.edit().remove(provider).commit();}
}
