package org.bouncycastle.crypto.tls;

import org.bouncycastle.crypto.Digest;

interface TlsHandshakeHash
    extends Digest
{
    void init(TlsContext context);

    TlsHandshakeHash notifyPRFDetermined();

    void trackHashAlgorithm(short hashAlgorithm);

    void sealHashAlgorithms();

    void stopTracking();

    Digest forkPRFHash();
}
