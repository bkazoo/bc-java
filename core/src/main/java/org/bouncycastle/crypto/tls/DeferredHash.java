package org.bouncycastle.crypto.tls;

import java.util.Enumeration;
import java.util.Hashtable;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.util.Shorts;

/**
 * Buffers input until the hash algorithm is determined.
 */
class DeferredHash
    implements TlsHandshakeHash
{
    protected static final int BUFFERING_HASH_LIMIT = 4;

    protected TlsContext context;

    private DigestInputBuffer buf;
    private Hashtable hashes;
    private Short prfHashAlgorithm;

    DeferredHash()
    {
        this.buf = new DigestInputBuffer();
        this.hashes = new Hashtable();
        this.prfHashAlgorithm = null;
    }

    public void init(TlsContext context)
    {
        this.context = context;
    }

    public TlsHandshakeHash notifyPRFDetermined()
    {
        int prfAlgorithm = context.getSecurityParameters().getPrfAlgorithm();
        if (prfAlgorithm == PRFAlgorithm.tls_prf_legacy)
        {
            CombinedHash legacyHash = new CombinedHash();
            legacyHash.init(context);
            buf.updateDigest(legacyHash);
            return legacyHash.notifyPRFDetermined();
        }

        this.prfHashAlgorithm = Shorts.valueOf(TlsUtils.getHashAlgorithmForPRFAlgorithm(prfAlgorithm));

        checkTrackingHash(prfHashAlgorithm);

        return this;
    }

    public void trackHashAlgorithm(short hashAlgorithm)
    {
        if (buf == null)
        {
            throw new IllegalStateException("Too late to track more hash algorithms");
        }

        checkTrackingHash(Shorts.valueOf(hashAlgorithm));
    }

    public void sealHashAlgorithms()
    {
        checkStopBuffering();
    }

    public void stopTracking()
    {
        if (hashes.size() > 1)
        {
            Digest prfHash = (Digest)hashes.get(prfHashAlgorithm);
            hashes = new Hashtable();
            hashes.put(prfHashAlgorithm, prfHash);
        }

        checkStopBuffering();
    }

    public Digest forkPRFHash()
    {
        if (buf != null)
        {
            Digest prfHash = TlsUtils.createHash(prfHashAlgorithm.shortValue());
            buf.updateDigest(prfHash);
            return prfHash;
        }

        return TlsUtils.cloneHash(prfHashAlgorithm.shortValue(), (Digest)hashes.get(prfHashAlgorithm));
    }

    public String getAlgorithmName()
    {
        throw new UnsupportedOperationException("Use fork() to get a definite Digest");
    }

    public int getDigestSize()
    {
        throw new UnsupportedOperationException("Use fork() to get a definite Digest");
    }

    public void update(byte input)
    {
        if (buf != null)
        {
            buf.write(input);
            return;
        }

        Enumeration e = hashes.elements();
        while (e.hasMoreElements())
        {
            Digest hash = (Digest)e.nextElement();
            hash.update(input);
        }
    }

    public void update(byte[] input, int inOff, int len)
    {
        if (buf != null)
        {
            buf.write(input, inOff, len);
            return;
        }

        Enumeration e = hashes.elements();
        while (e.hasMoreElements())
        {
            Digest hash = (Digest)e.nextElement();
            hash.update(input, inOff, len);
        }
    }

    public int doFinal(byte[] output, int outOff)
    {
        throw new UnsupportedOperationException("Use fork() to get a definite Digest");
    }

    public void reset()
    {
        if (buf != null)
        {
            buf.reset();
            return;
        }

        Enumeration e = hashes.elements();
        while (e.hasMoreElements())
        {
            Digest hash = (Digest)e.nextElement();
            hash.reset();
        }
    }

    protected void checkStopBuffering()
    {
        if (buf != null && hashes.size() <= BUFFERING_HASH_LIMIT)
        {
            Enumeration e = hashes.elements();
            while (e.hasMoreElements())
            {
                Digest hash = (Digest)e.nextElement();
                buf.updateDigest(hash);
            }

            this.buf = null;
        }
    }

    protected void checkTrackingHash(Short hashAlgorithm)
    {
        if (!hashes.containsKey(hashAlgorithm))
        {
            Digest hash = TlsUtils.createHash(hashAlgorithm.shortValue());
            hashes.put(hashAlgorithm, hash);
        }
    }
}
