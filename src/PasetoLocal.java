/*
 * MIT License
 *
 * Copyright (c) 2018 Nanne Baars
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import com.google.common.primitives.Bytes;
import org.spongycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;
import java.util.Arrays;
import java.util.Base64;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Verify.verify;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Base64.getUrlDecoder;
import static java.util.Base64.getUrlEncoder;


class PasetoLocal {

    /*
    static {
        Security.addProvider(new BouncyCastleProvider());
    }
    */
    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
    }

    private static final String LOCAL = "v1.local.";

    private PasetoLocal() {}

    /**
     * https://github.com/paragonie/paseto/blob/master/docs/01-Protocol-Versions/Version1.md#encrypt
     */
    public static String encrypt(byte[] key, String payload, String footer) {
        return encrypt(key, PasetoCryptoFunctions.randomBytes(), payload, footer);
    }

    /**
     * https://github.com/paragonie/paseto/blob/master/docs/01-Protocol-Versions/Version1.md#encrypt
     */
    static String encrypt(byte[] key, byte[] randomKey, String payload, String footer) {
        checkNotNull(key);
        checkNotNull(payload);
        checkArgument(key.length == 32, "key should be 32 bytes");

        //3
        byte[] nonce = getNonce(payload.getBytes(UTF_8), randomKey);

        //4
        byte[] ek = encryptionKey(key, nonce);
        byte[] ak = authenticationKey(key, nonce);

        //5
        byte[] cipherText = PasetoCryptoFunctions.encryptAesCtr(ek, Arrays.copyOfRange(nonce, 16, 32), payload.getBytes(UTF_8));

        //6
        byte[] preAuth = PasetoUtil.pae(LOCAL.getBytes(UTF_8), nonce, cipherText, footer.getBytes(UTF_8));

        //7
        byte[] t = PasetoCryptoFunctions.hmac384(ak, preAuth);

        //8
        String signedToken = LOCAL + getUrlEncoder().withoutPadding().encodeToString(Bytes.concat(nonce, cipherText, t));

        if (!isNullOrEmpty(footer)) {
            signedToken = signedToken + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(footer.getBytes(UTF_8));
        }
        return signedToken;
    }

    private static byte[] getNonce(byte[] payload, byte[] randomKey) {
        return Arrays.copyOfRange(PasetoCryptoFunctions.hmac384(randomKey, payload), 0, 32);
    }

    private static byte[] encryptionKey(byte[] key, byte[] nonce) {
        return PasetoCryptoFunctions.hkdfSha384(key, Arrays.copyOfRange(nonce, 0, 16), "paseto-encryption-key".getBytes(UTF_8));
    }

    private static byte[] authenticationKey(byte[] key, byte[] nonce) {
        return PasetoCryptoFunctions.hkdfSha384(key, Arrays.copyOfRange(nonce, 0, 16), "paseto-auth-key-for-aead".getBytes(UTF_8));
    }

    /**
     * https://github.com/paragonie/paseto/blob/master/docs/01-Protocol-Versions/Version1.md#decrypt
     */
    static String decrypt(byte[] key, String token, String footer) {
        checkNotNull(key);
        checkNotNull(token);
        checkArgument(key.length == 32, "Secret key should be 32 bytes");

        String[] tokenParts = token.split("\\.");
        verify(tokenParts.length == 3 || tokenParts.length == 4, "Token should contain at least 3 parts");

        //1
        if (!isNullOrEmpty(footer)) {
            verify(Arrays.equals(getUrlDecoder().decode(tokenParts[3]), footer.getBytes(UTF_8)), "footer does not match");
        }

        //2
        verify(token.startsWith(LOCAL), "Token should start with " + LOCAL);

        //3
        byte[] ct = getUrlDecoder().decode(tokenParts[2]);
        byte[] nonce = Arrays.copyOfRange(ct, 0, 32);
        byte[] t = Arrays.copyOfRange(ct, ct.length - 48, ct.length);
        byte[] c = Arrays.copyOfRange(ct, 32, ct.length - 48);

        //4
        byte[] ek = encryptionKey(key, nonce);
        byte[] ak = authenticationKey(key, nonce);

        //5
        byte[] preAuth = PasetoUtil.pae(LOCAL.getBytes(UTF_8), nonce, c, footer.getBytes(UTF_8));

        //6
        byte[] t2 = PasetoCryptoFunctions.hmac384(ak, preAuth);

        //7
        verify(Arrays.equals(t, t2));

        //8
        byte[] message = PasetoCryptoFunctions.decryptAesCtr(ek, Arrays.copyOfRange(nonce, 16, 32), c);
        return new String(message, UTF_8);
    }
}
