package org.bouncycastle.math.ec;

import java.math.BigInteger;

/**
 * base class for points on elliptic curves.
 */
public abstract class ECPoint
{
    protected static ECFieldElement[] EMPTY_ZS = new ECFieldElement[0];

    protected static ECFieldElement[] getInitialZCoords(ECCurve curve)
    {
        // Cope with null curve, most commonly used by implicitlyCa
        int coord = null == curve ? ECCurve.COORD_AFFINE : curve.getCoordinateSystem();

        switch (coord)
        {
        case ECCurve.COORD_AFFINE:
        case ECCurve.COORD_LAMBDA_AFFINE:
            return EMPTY_ZS;
        default:
            break;
        }

        ECFieldElement one = curve.fromBigInteger(ECConstants.ONE);

        switch (coord)
        {
        case ECCurve.COORD_HOMOGENEOUS:
        case ECCurve.COORD_JACOBIAN:
        case ECCurve.COORD_LAMBDA_PROJECTIVE:
            return new ECFieldElement[]{ one };
        case ECCurve.COORD_JACOBIAN_CHUDNOVSKY:
            return new ECFieldElement[]{ one, one, one };
        case ECCurve.COORD_JACOBIAN_MODIFIED:
            return new ECFieldElement[]{ one, curve.getA() };
        default:
            throw new IllegalArgumentException("unknown coordinate system");
        }
    }

    protected ECCurve curve;
    protected ECFieldElement x;
    protected ECFieldElement y;
    protected ECFieldElement[] zs;

    protected boolean withCompression;

    protected PreCompInfo preCompInfo = null;

    protected ECPoint(ECCurve curve, ECFieldElement x, ECFieldElement y)
    {
        this(curve, x, y, getInitialZCoords(curve));
    }

    protected ECPoint(ECCurve curve, ECFieldElement x, ECFieldElement y, ECFieldElement[] zs)
    {
        this.curve = curve;
        this.x = x;
        this.y = y;
        this.zs = zs;
    }

    public ECCurve getCurve()
    {
        return curve;
    }

    protected int getCurveCoordinateSystem()
    {
        // Cope with null curve, most commonly used by implicitlyCa
        return null == curve ? ECCurve.COORD_AFFINE : curve.getCoordinateSystem();
    }

    /**
     * Normalizes this point, and then returns the affine x-coordinate.
     * 
     * Note: normalization can be expensive, this method is deprecated in favour
     * of caller-controlled normalization.
     * 
     * @deprecated Use getAffineXCoord, or normalize() and getXCoord(), instead
     */
    public ECFieldElement getX()
    {
        return normalize().getXCoord();
    }


    /**
     * Normalizes this point, and then returns the affine y-coordinate.
     * 
     * Note: normalization can be expensive, this method is deprecated in favour
     * of caller-controlled normalization.
     * 
     * @deprecated Use getAffineYCoord, or normalize() and getYCoord(), instead
     */
    public ECFieldElement getY()
    {
        return normalize().getYCoord();
    }

    /**
     * Returns the affine x-coordinate after checking that this point is normalized.
     * 
     * @return The affine x-coordinate of this point
     * @throws IllegalStateException if the point is not normalized
     */
    public ECFieldElement getAffineXCoord()
    {
        checkNormalized();
        return getXCoord();
    }

    /**
     * Returns the affine y-coordinate after checking that this point is normalized
     * 
     * @return The affine y-coordinate of this point
     * @throws IllegalStateException if the point is not normalized
     */
    public ECFieldElement getAffineYCoord()
    {
        checkNormalized();
        return getYCoord();
    }

    /**
     * Returns the x-coordinate.
     * 
     * Caution: depending on the curve's coordinate system, this may not be the same value as in an
     * affine coordinate system; use normalize() to get a point where the coordinates have their
     * affine values, or use getAffineXCoord if you expect the point to already have been
     * normalized.
     * 
     * @return the x-coordinate of this point
     */
    public ECFieldElement getXCoord()
    {
        return x;
    }

    /**
     * Returns the y-coordinate.
     * 
     * Caution: depending on the curve's coordinate system, this may not be the same value as in an
     * affine coordinate system; use normalize() to get a point where the coordinates have their
     * affine values, or use getAffineYCoord if you expect the point to already have been
     * normalized.
     * 
     * @return the y-coordinate of this point
     */
    public ECFieldElement getYCoord()
    {
        return y;
    }

    public ECFieldElement getZCoord(int index)
    {
        return (index < 0 || index >= zs.length) ? null : zs[index];
    }

    public ECFieldElement[] getZCoords()
    {
        int zsLen = zs.length;
        if (zsLen == 0)
        {
            return zs;
        }
        ECFieldElement[] copy = new ECFieldElement[zsLen];
        System.arraycopy(zs, 0, copy, 0, zsLen);
        return copy;
    }

    protected ECFieldElement getRawXCoord()
    {
        return x;
    }

    protected ECFieldElement getRawYCoord()
    {
        return y;
    }

    protected void checkNormalized()
    {
        if (!isNormalized())
        {
            throw new IllegalStateException("point not in normal form");
        }
    }

    public boolean isNormalized()
    {
        int coord = getCurveCoordinateSystem();

        return coord == ECCurve.COORD_AFFINE
            || coord == ECCurve.COORD_LAMBDA_AFFINE
            || isInfinity()
            || zs[0].bitLength() == 1;
    }

    /**
     * Normalization ensures that any projective coordinate is 1, and therefore that the x, y
     * coordinates reflect those of the equivalent point in an affine coordinate system.
     * 
     * @return a new ECPoint instance representing the same point, but with normalized coordinates
     */
    public ECPoint normalize()
    {
        if (isInfinity())
        {
            return this;
        }

        switch (getCurveCoordinateSystem())
        {
        case ECCurve.COORD_AFFINE:
        case ECCurve.COORD_LAMBDA_AFFINE:
        {
            return this;
        }
        default:
        {
            ECFieldElement Z1 = getZCoord(0);
            if (Z1.bitLength() == 1)
            {
                return this;
            }

            return normalize(Z1.invert());
        }
        }
    }

    ECPoint normalize(ECFieldElement zInv)
    {
        switch (getCurveCoordinateSystem())
        {
        case ECCurve.COORD_HOMOGENEOUS:
        case ECCurve.COORD_LAMBDA_PROJECTIVE:
        {
            return createScaledPoint(zInv, zInv);
        }
        case ECCurve.COORD_JACOBIAN:
        case ECCurve.COORD_JACOBIAN_CHUDNOVSKY:
        case ECCurve.COORD_JACOBIAN_MODIFIED:
        {
            ECFieldElement zInv2 = zInv.square(), zInv3 = zInv2.multiply(zInv);
            return createScaledPoint(zInv2, zInv3);
        }
        default:
        {
            throw new IllegalStateException("not a projective coordinate system");
        }
        }
    }

    protected ECPoint createScaledPoint(ECFieldElement sx, ECFieldElement sy)
    {
        return getCurve().createRawPoint(getRawXCoord().multiply(sx), getRawYCoord().multiply(sy), withCompression);
    }

    public boolean isInfinity()
    {
        return x == null || y == null || (zs.length > 0 && zs[0].isZero());
    }

    public boolean isCompressed()
    {
        return withCompression;
    }

    public boolean equals(ECPoint other)
    {
        if (null == other)
        {
            return false;
        }

        ECCurve c1 = getCurve(), c2 = other.getCurve();
        boolean n1 = (null == c1), n2 = (null == c2);
        boolean i1 = isInfinity(), i2 = other.isInfinity();

        if (i1 || i2)
        {
            return (i1 && i2) && (n1 || n2 || c1.equals(c2));
        }

        ECPoint p1 = this, p2 = other;
        if (n1 && n2)
        {
            // Points with null curve are in affine form, so already normalized
        }
        else if (n1)
        {
            p2 = p2.normalize();
        }
        else if (n2)
        {
            p1 = p1.normalize();
        }
        else if (!c1.equals(c2))
        {
            return false;
        }
        else
        {
            // TODO Consider just requiring already normalized, to avoid silent performance degradation

            ECPoint[] points = new ECPoint[]{ this, c1.importPoint(p2) };

            // TODO This is a little strong, really only requires coZNormalizeAll to get Zs equal
            c1.normalizeAll(points);

            p1 = points[0];
            p2 = points[1];
        }

        return p1.getXCoord().equals(p2.getXCoord()) && p1.getYCoord().equals(p2.getYCoord());
    }

    public boolean equals(Object other)
    {
        if (other == this)
        {
            return true;
        }

        if (!(other instanceof ECPoint))
        {
            return false;
        }

        return equals((ECPoint)other);
    }

    public int hashCode()
    {
        ECCurve c = getCurve();
        int hc = (null == c) ? 0 : ~c.hashCode();

        if (!isInfinity())
        {
            // TODO Consider just requiring already normalized, to avoid silent performance degradation

            ECPoint p = normalize();

            hc ^= p.getXCoord().hashCode() * 17;
            hc ^= p.getYCoord().hashCode() * 257;
        }

        return hc;
    }

    public String toString()
    {
        if (isInfinity())
        {
            return "INF";
        }

        StringBuffer sb = new StringBuffer();
        sb.append('(');
        sb.append(getRawXCoord());
        sb.append(',');
        sb.append(getRawYCoord());
        for (int i = 0; i < zs.length; ++i)
        {
            sb.append(',');
            sb.append(zs[i]);
        }
        sb.append(')');
        return sb.toString();
    }

    public byte[] getEncoded()
    {
        return getEncoded(withCompression);
    }

    /**
     * return the field element encoded with point compression. (S 4.3.6)
     */
    public byte[] getEncoded(boolean compressed)
    {
        if (this.isInfinity())
        {
            return new byte[1];
        }

        ECPoint normed = normalize();

        byte[] X = normed.getXCoord().getEncoded();

        if (compressed)
        {
            byte[] PO = new byte[X.length + 1];
            PO[0] = (byte)(normed.getCompressionYTilde() ? 0x03 : 0x02);
            System.arraycopy(X, 0, PO, 1, X.length);
            return PO;
        }

        byte[] Y = normed.getYCoord().getEncoded();

        byte[] PO = new byte[X.length + Y.length + 1];
        PO[0] = 0x04;
        System.arraycopy(X, 0, PO, 1, X.length);
        System.arraycopy(Y, 0, PO, X.length + 1, Y.length);
        return PO;
    }

    protected abstract boolean getCompressionYTilde();

    public abstract ECPoint add(ECPoint b);

    public abstract ECPoint negate();

    public abstract ECPoint subtract(ECPoint b);

    public ECPoint timesPow2(int e)
    {
        if (e < 0)
        {
            throw new IllegalArgumentException("'e' cannot be negative");
        }

        ECPoint p = this;
        while (--e >= 0)
        {
            p = p.twice();
        }
        return p;
    }

    public abstract ECPoint twice();

    public ECPoint twicePlus(ECPoint b)
    {
        return twice().add(b);
    }

    public ECPoint threeTimes()
    {
        return twicePlus(this);
    }

    /**
     * Multiplies this <code>ECPoint</code> by the given number.
     * @param k The multiplicator.
     * @return <code>k * this</code>.
     */
    public ECPoint multiply(BigInteger k)
    {
        return getCurve().getMultiplier().multiply(this, k);
    }

    /**
     * Elliptic curve points over Fp
     */
    public static class Fp extends ECPoint
    {
        /**
         * Create a point which encodes with point compression.
         * 
         * @param curve the curve to use
         * @param x affine x co-ordinate
         * @param y affine y co-ordinate
         * 
         * @deprecated Use ECCurve.createPoint to construct points
         */
        public Fp(ECCurve curve, ECFieldElement x, ECFieldElement y)
        {
            this(curve, x, y, false);
        }

        /**
         * Create a point that encodes with or without point compresion.
         * 
         * @param curve the curve to use
         * @param x affine x co-ordinate
         * @param y affine y co-ordinate
         * @param withCompression if true encode with point compression
         * 
         * @deprecated per-point compression property will be removed, refer {@link #getEncoded(boolean)}
         */
        public Fp(ECCurve curve, ECFieldElement x, ECFieldElement y, boolean withCompression)
        {
            super(curve, x, y);

            if ((x != null && y == null) || (x == null && y != null))
            {
                throw new IllegalArgumentException("Exactly one of the field elements is null");
            }

            this.withCompression = withCompression;
        }

        Fp(ECCurve curve, ECFieldElement x, ECFieldElement y, ECFieldElement[] zs, boolean withCompression)
        {
            super(curve, x, y, zs);

            this.withCompression = withCompression;
        }

        protected boolean getCompressionYTilde()
        {
            return getAffineYCoord().testBitZero();
        }

        public ECFieldElement getZCoord(int index)
        {
            if (index == 1 && ECCurve.COORD_JACOBIAN_MODIFIED == getCurveCoordinateSystem())
            {
                return getJacobianModifiedW();
            }

            return super.getZCoord(index);
        }

        // B.3 pg 62
        public ECPoint add(ECPoint b)
        {
            if (this.isInfinity())
            {
                return b;
            }
            if (b.isInfinity())
            {
                return this;
            }
            if (this == b)
            {
                return twice();
            }

            ECCurve curve = getCurve();
            int coord = curve.getCoordinateSystem();

            ECFieldElement X1 = this.x, Y1 = this.y;
            ECFieldElement X2 = b.x, Y2 = b.y;

            switch (coord)
            {
            case ECCurve.COORD_AFFINE:
            {
                ECFieldElement dx = X2.subtract(X1), dy = Y2.subtract(Y1);

                if (dx.isZero())
                {
                    if (dy.isZero())
                    {
                        // this == b, i.e. this must be doubled
                        return twice();
                    }

                    // this == -b, i.e. the result is the point at infinity
                    return curve.getInfinity();
                }

                ECFieldElement gamma = dy.divide(dx);
                ECFieldElement X3 = gamma.square().subtract(X1).subtract(X2);
                ECFieldElement Y3 = gamma.multiply(X1.subtract(X3)).subtract(Y1);

                return new ECPoint.Fp(curve, X3, Y3, this.withCompression);
            }

            case ECCurve.COORD_HOMOGENEOUS:
            {
                ECFieldElement Z1 = this.zs[0];
                ECFieldElement Z2 = b.zs[0];

                boolean Z1IsOne = Z1.bitLength() == 1;
                boolean Z2IsOne = Z2.bitLength() == 1;

                ECFieldElement u1 = Z1IsOne ? Y2 : Y2.multiply(Z1);
                ECFieldElement u2 = Z2IsOne ? Y1 : Y1.multiply(Z2);
                ECFieldElement u = u1.subtract(u2);
                ECFieldElement v1 = Z1IsOne ? X2 : X2.multiply(Z1);
                ECFieldElement v2 = Z2IsOne ? X1 : X1.multiply(Z2);
                ECFieldElement v = v1.subtract(v2);

                // Check if b == this or b == -this
                if (v.isZero())
                {
                    if (u.isZero())
                    {
                        // this == b, i.e. this must be doubled
                        return this.twice();
                    }

                    // this == -b, i.e. the result is the point at infinity
                    return curve.getInfinity();
                }

                // TODO Optimize for when w == 1
                ECFieldElement w = Z1IsOne ? Z2 : Z2IsOne ? Z1 : Z1.multiply(Z2);
                ECFieldElement vSquared = v.square();
                ECFieldElement vCubed = vSquared.multiply(v);
                ECFieldElement vSquaredV2 = vSquared.multiply(v2);
                ECFieldElement A = u.square().multiply(w).subtract(vCubed).subtract(two(vSquaredV2));

                ECFieldElement X3 = v.multiply(A);
                ECFieldElement Y3 = vSquaredV2.subtract(A).multiply(u).subtract(vCubed.multiply(u2));
                ECFieldElement Z3 = vCubed.multiply(w);

                return new ECPoint.Fp(curve, X3, Y3, new ECFieldElement[]{ Z3 }, this.withCompression);
            }

            case ECCurve.COORD_JACOBIAN:
            case ECCurve.COORD_JACOBIAN_MODIFIED:
            {
                ECFieldElement Z1 = this.zs[0];
                ECFieldElement Z2 = b.zs[0];

                boolean Z1IsOne = Z1.bitLength() == 1;

                ECFieldElement X3, Y3, Z3, Z3Squared = null;

                if (!Z1IsOne && Z1.equals(Z2))
                {
                    // TODO Make this available as public method coZAdd?

                    ECFieldElement dx = X1.subtract(X2), dy = Y1.subtract(Y2);
                    if (dx.isZero())
                    {
                        if (dy.isZero())
                        {
                            return twice();
                        }
                        return curve.getInfinity();
                    }

                    ECFieldElement C = dx.square();
                    ECFieldElement W1 = X1.multiply(C), W2 = X2.multiply(C);
                    ECFieldElement A1 = W1.subtract(W2).multiply(Y1);

                    X3 = dy.square().subtract(W1).subtract(W2);
                    Y3 = W1.subtract(X3).multiply(dy).subtract(A1);
                    Z3 = dx;

                    if (Z1IsOne)
                    {
                        Z3Squared = C;
                    }
                    else
                    {
                        Z3 = Z3.multiply(Z1);
                    }
                }
                else
                {
                    ECFieldElement Z1Squared, U2, S2;
                    if (Z1IsOne)
                    {
                        Z1Squared = Z1; U2 = X2; S2 = Y2;
                    }
                    else
                    {
                        Z1Squared = Z1.square();
                        U2 = Z1Squared.multiply(X2);
                        ECFieldElement Z1Cubed = Z1Squared.multiply(Z1);
                        S2 = Z1Cubed.multiply(Y2);
                    }

                    boolean Z2IsOne = Z2.bitLength() == 1;
                    ECFieldElement Z2Squared, U1, S1;
                    if (Z2IsOne)
                    {
                        Z2Squared = Z2; U1 = X1; S1 = Y1;
                    }
                    else
                    {
                        Z2Squared = Z2.square();
                        U1 = Z2Squared.multiply(X1); 
                        ECFieldElement Z2Cubed = Z2Squared.multiply(Z2);
                        S1 = Z2Cubed.multiply(Y1);
                    }

                    ECFieldElement H = U1.subtract(U2);
                    ECFieldElement R = S1.subtract(S2);
    
                    // Check if b == this or b == -this
                    if (H.isZero())
                    {
                        if (R.isZero())
                        {
                            // this == b, i.e. this must be doubled
                            return this.twice();
                        }
    
                        // this == -b, i.e. the result is the point at infinity
                        return curve.getInfinity();
                    }
    
                    ECFieldElement HSquared = H.square();
                    ECFieldElement G = HSquared.multiply(H);
                    ECFieldElement V = HSquared.multiply(U1);
    
                    X3 = R.square().add(G).subtract(two(V));
                    Y3 = V.subtract(X3).multiply(R).subtract(S1.multiply(G));
    
                    Z3 = H;
                    if (!Z1IsOne)
                    {
                        Z3 = Z3.multiply(Z1);
                    }
                    if (!Z2IsOne)
                    {
                        Z3 = Z3.multiply(Z2);
                    }
    
                    // Alternative calculation of Z3 using fast square
    //                X3 = four(X3);
    //                Y3 = eight(Y3);
    //                Z3 = doubleProductFromSquares(Z1, Z2, Z1Squared, Z2Squared).multiply(H);
                    
                    if (Z3 == H)
                    {
                        Z3Squared = HSquared;
                    }
                }

                ECFieldElement[] zs;
                if (coord == ECCurve.COORD_JACOBIAN_MODIFIED)
                {
                    // TODO If the result will only be used in a subsequent addition, we don't need W3
                    ECFieldElement W3 = calculateJacobianModifiedW(Z3, Z3Squared);

                    zs = new ECFieldElement[]{ Z3, W3 };
                }
                else
                {
                    zs = new ECFieldElement[]{ Z3 };
                }

                return new ECPoint.Fp(curve, X3, Y3, zs, this.withCompression);
            }
            default:
            {
                throw new UnsupportedOperationException("unsupported coordinate system");
            }
            }
        }

        // B.3 pg 62
        public ECPoint twice()
        {
            if (isInfinity())
            {
                return this;
            }

            ECCurve curve = getCurve();

            ECFieldElement Y1 = this.y;
            if (Y1.isZero()) 
            {
                return curve.getInfinity();
            }

            int coord = curve.getCoordinateSystem();

            ECFieldElement X1 = this.x;

            switch (coord)
            {
            case ECCurve.COORD_AFFINE:
            {
                ECFieldElement X1Squared = X1.square();
                ECFieldElement gamma = three(X1Squared).add(getCurve().getA()).divide(two(Y1));
                ECFieldElement X3 = gamma.square().subtract(two(X1));
                ECFieldElement Y3 = gamma.multiply(X1.subtract(X3)).subtract(Y1);
    
                return new ECPoint.Fp(curve, X3, Y3, this.withCompression);
            }

            case ECCurve.COORD_HOMOGENEOUS:
            {
                ECFieldElement Z1 = this.zs[0];

                boolean Z1IsOne = Z1.bitLength() == 1;
                ECFieldElement Z1Squared = Z1IsOne ? Z1 : Z1.square();

                // TODO Optimize for small negative a4 and -3
                ECFieldElement w = curve.getA();
                if (!Z1IsOne)
                {
                    w = w.multiply(Z1Squared);
                }
                w = w.add(three(X1.square()));
                
                ECFieldElement s = Z1IsOne ? Y1 : Y1.multiply(Z1);
                ECFieldElement t = Z1IsOne ? Y1.square() : s.multiply(Y1);
                ECFieldElement B = X1.multiply(t);
                ECFieldElement _4B = four(B);
                ECFieldElement h = w.square().subtract(two(_4B));

                ECFieldElement X3 = two(h.multiply(s));
                ECFieldElement Y3 = w.multiply(_4B.subtract(h)).subtract(two(two(t).square()));
                ECFieldElement _4sSquared = Z1IsOne ? four(t) : two(s).square();
                ECFieldElement Z3 = two(_4sSquared).multiply(s);

                return new ECPoint.Fp(curve, X3, Y3, new ECFieldElement[]{ Z3 }, this.withCompression);
            }

            case ECCurve.COORD_JACOBIAN:
            {
                ECFieldElement Z1 = this.zs[0];

                boolean Z1IsOne = Z1.bitLength() == 1;
                ECFieldElement Z1Squared = Z1IsOne ? Z1 : Z1.square();

                ECFieldElement Y1Squared = Y1.square();
                ECFieldElement T = Y1Squared.square();

                ECFieldElement a4 = curve.getA();
                ECFieldElement a4Neg = a4.negate();

                ECFieldElement M, S;
                if (a4Neg.toBigInteger().equals(BigInteger.valueOf(3)))
                {
                    M = three(X1.add(Z1Squared).multiply(X1.subtract(Z1Squared)));
                    S = four(Y1Squared.multiply(X1));
                }
                else
                {
                    ECFieldElement X1Squared = X1.square();
                    M = three(X1Squared);
                    if (Z1IsOne)
                    {
                        M = M.add(a4);
                    }
                    else
                    {
                        ECFieldElement Z1Pow4 = Z1Squared.square();
                        if (a4Neg.bitLength() < a4.bitLength())
                        {
                            M = M.subtract(Z1Pow4.multiply(a4Neg));
                        }
                        else
                        {
                            M = M.add(Z1Pow4.multiply(a4));
                        }
                    }
                    S = two(doubleProductFromSquares(X1, Y1Squared, X1Squared, T));
                }

                ECFieldElement X3 = M.square().subtract(two(S));
                ECFieldElement Y3 = S.subtract(X3).multiply(M).subtract(eight(T));

                ECFieldElement Z3 = two(Y1);
                if (!Z1IsOne)
                {
                    Z3 = Z3.multiply(Z1);
                }

                // Alternative calculation of Z3 using fast square
//                ECFieldElement Z3 = doubleProductFromSquares(Y1, Z1, Y1Squared, Z1Squared);

                return new ECPoint.Fp(curve, X3, Y3, new ECFieldElement[]{ Z3 }, this.withCompression);
            }

            case ECCurve.COORD_JACOBIAN_MODIFIED:
            {
                return twiceJacobianModified(true);
            }

            default:
            {
                throw new UnsupportedOperationException("unsupported coordinate system");
            }
            }
        }

        public ECPoint twicePlus(ECPoint b)
        {
            if (this == b)
            {
                return threeTimes();
            }
            if (isInfinity())
            {
                return b;
            }
            if (b.isInfinity())
            {
                return twice();
            }

            ECFieldElement Y1 = this.y;
            if (Y1.isZero()) 
            {
                return b;
            }

            ECCurve curve = getCurve();
            int coord = curve.getCoordinateSystem();

            switch (coord)
            {
            case ECCurve.COORD_AFFINE:
            {
                ECFieldElement X1 = this.x;
                ECFieldElement X2 = b.x, Y2 = b.y;

                ECFieldElement dx = X2.subtract(X1), dy = Y2.subtract(Y1);

                if (dx.isZero())
                {
                    if (dy.isZero())
                    {
                        // this == b i.e. the result is 3P
                        return threeTimes();
                    }

                    // this == -b, i.e. the result is P
                    return this;
                }

                /*
                 * Optimized calculation of 2P + Q, as described in "Trading Inversions for
                 * Multiplications in Elliptic Curve Cryptography", by Ciet, Joye, Lauter, Montgomery.
                 */

                ECFieldElement X = dx.square(), Y = dy.square();
                ECFieldElement d = X.multiply(two(X1).add(X2)).subtract(Y);
                if (d.isZero())
                {
                    return curve.getInfinity();
                }

                ECFieldElement D = d.multiply(dx);
                ECFieldElement I = D.invert();
                ECFieldElement L1 = d.multiply(I).multiply(dy);
                ECFieldElement L2 = two(Y1).multiply(X).multiply(dx).multiply(I).subtract(L1);
                ECFieldElement X4 = (L2.subtract(L1)).multiply(L1.add(L2)).add(X2);
                ECFieldElement Y4 = (X1.subtract(X4)).multiply(L2).subtract(Y1);

                return new ECPoint.Fp(curve, X4, Y4, this.withCompression);
            }
            case ECCurve.COORD_JACOBIAN_MODIFIED:
            {
                return twiceJacobianModified(false).add(b);
            }
            default:
            {
                return twice().add(b);
            }
            }
        }

        public ECPoint threeTimes()
        {
            if (isInfinity() || this.y.isZero())
            {
                return this;
            }

            ECCurve curve = getCurve();
            int coord = curve.getCoordinateSystem();

            switch (coord)
            {
            case ECCurve.COORD_AFFINE:
            {
                ECFieldElement X1 = this.x, Y1 = this.y;

                ECFieldElement _2Y1 = two(Y1); 
                ECFieldElement X = _2Y1.square();
                ECFieldElement Z = three(X1.square()).add(getCurve().getA());
                ECFieldElement Y = Z.square();

                ECFieldElement d = three(X1).multiply(X).subtract(Y);
                if (d.isZero())
                {
                    return getCurve().getInfinity();
                }

                ECFieldElement D = d.multiply(_2Y1); 
                ECFieldElement I = D.invert();
                ECFieldElement L1 = d.multiply(I).multiply(Z);
                ECFieldElement L2 = X.square().multiply(I).subtract(L1);

                ECFieldElement X4 = (L2.subtract(L1)).multiply(L1.add(L2)).add(X1);
                ECFieldElement Y4 = (X1.subtract(X4)).multiply(L2).subtract(Y1); 
                return new ECPoint.Fp(curve, X4, Y4, this.withCompression);
            }
            case ECCurve.COORD_JACOBIAN_MODIFIED:
            {
                return twiceJacobianModified(false).add(this);
            }
            default:
            {
                // NOTE: Be careful about recursions between twicePlus and threeTimes
                return twice().add(this);
            }
            }
        }

        protected ECFieldElement two(ECFieldElement x)
        {
            return x.add(x);
        }

        protected ECFieldElement three(ECFieldElement x)
        {
            return two(x).add(x);
        }

        protected ECFieldElement four(ECFieldElement x)
        {
            return two(two(x));
        }

        protected ECFieldElement eight(ECFieldElement x)
        {
            return four(two(x));
        }

        protected ECFieldElement doubleProductFromSquares(ECFieldElement a, ECFieldElement b,
            ECFieldElement aSquared, ECFieldElement bSquared)
        {
            /*
             * NOTE: If squaring in the field is faster than multiplication, then this is a quicker
             * way to calculate 2.A.B, if A^2 and B^2 are already known.
             */
            return a.add(b).square().subtract(aSquared).subtract(bSquared);
        }

        // D.3.2 pg 102 (see Note:)
        public ECPoint subtract(ECPoint b)
        {
            if (b.isInfinity())
            {
                return this;
            }

            // Add -b
            return add(b.negate());
        }

        public ECPoint negate()
        {
            if (this.isInfinity())
            {
                return this;
            }

            ECCurve curve = getCurve();
            int coord = curve.getCoordinateSystem();

            if (ECCurve.COORD_AFFINE != coord)
            {
                return new ECPoint.Fp(curve, this.x, this.y.negate(), this.zs, this.withCompression);
            }

            return new ECPoint.Fp(curve, this.x, this.y.negate(), this.withCompression);
        }

        protected ECFieldElement calculateJacobianModifiedW(ECFieldElement Z, ECFieldElement ZSquared)
        {
            if (ZSquared == null)
            {
                ZSquared = Z.square();
            }

            ECFieldElement W = ZSquared.square();
            ECFieldElement a4 = getCurve().getA();
            ECFieldElement a4Neg = a4.negate();
            if (a4Neg.bitLength() < a4.bitLength())
            {
                W = W.multiply(a4Neg).negate();
            }
            else
            {
                W = W.multiply(a4);
            }
            return W;
        }

        protected ECFieldElement getJacobianModifiedW()
        {
            ECFieldElement W = this.zs[1];
            if (W == null)
            {
                // NOTE: Rarely, twicePlus will result in the need for a lazy W1 calculation here
                this.zs[1] = W = calculateJacobianModifiedW(this.zs[0], null);
            }
            return W;
        }

        protected ECPoint.Fp twiceJacobianModified(boolean calculateW)
        {
            ECFieldElement X1 = this.x, Y1 = this.y, Z1 = this.zs[0], W1 = getJacobianModifiedW();

            ECFieldElement X1Squared = X1.square();
            ECFieldElement M = three(X1Squared).add(W1);
            ECFieldElement Y1Squared = Y1.square();
            ECFieldElement T = Y1Squared.square();
            ECFieldElement S = two(doubleProductFromSquares(X1, Y1Squared, X1Squared, T));
            ECFieldElement X3 = M.square().subtract(two(S));
            ECFieldElement _8T = eight(T);
            ECFieldElement Y3 = M.multiply(S.subtract(X3)).subtract(_8T);
            ECFieldElement W3 = calculateW ? two(_8T.multiply(W1)) : null;
            ECFieldElement Z3 = two(Z1.bitLength() == 1 ? Y1 : Y1.multiply(Z1));

            return new ECPoint.Fp(getCurve(), X3, Y3, new ECFieldElement[]{ Z3, W3 }, this.withCompression);
        }
    }

    /**
     * Elliptic curve points over F2m
     */
    public static class F2m extends ECPoint
    {
        /**
         * @param curve base curve
         * @param x x point
         * @param y y point
         * 
         * @deprecated Use ECCurve.createPoint to construct points
         */
        public F2m(ECCurve curve, ECFieldElement x, ECFieldElement y)
        {
            this(curve, x, y, false);
        }
        
        /**
         * @param curve base curve
         * @param x x point
         * @param y y point
         * @param withCompression true if encode with point compression.
         * 
         * @deprecated per-point compression property will be removed, refer {@link #getEncoded(boolean)}
         */
        public F2m(ECCurve curve, ECFieldElement x, ECFieldElement y, boolean withCompression)
        {
            super(curve, x, y);

            if ((x != null && y == null) || (x == null && y != null))
            {
                throw new IllegalArgumentException("Exactly one of the field elements is null");
            }

            if (x != null)
            {
                // Check if x and y are elements of the same field
                ECFieldElement.F2m.checkFieldElements(this.x, this.y);

                // Check if x and a are elements of the same field
                if (curve != null)
                {
                    ECFieldElement.F2m.checkFieldElements(this.x, this.curve.getA());
                }
            }

            this.withCompression = withCompression;

//            checkCurveEquation();
        }

        F2m(ECCurve curve, ECFieldElement x, ECFieldElement y, ECFieldElement[] zs, boolean withCompression)
        {
            super(curve, x, y, zs);

            this.withCompression = withCompression;

//            checkCurveEquation();
        }

        public ECFieldElement getYCoord()
        {
            int coord = getCurveCoordinateSystem();

            switch (coord)
            {
            case ECCurve.COORD_LAMBDA_AFFINE:
            case ECCurve.COORD_LAMBDA_PROJECTIVE:
            {
                // TODO The X == 0 stuff needs further thought
                if (isInfinity() || x.isZero())
                {
                    return y;
                }

                // Y is actually Lambda (X + Y/X) here; convert to affine value on the fly
                ECFieldElement X = x, L = y;
                ECFieldElement Y = L.subtract(X).multiply(X);
                if (ECCurve.COORD_LAMBDA_PROJECTIVE == coord)
                {
                    ECFieldElement Z = zs[0];
                    if (Z.bitLength() != 1)
                    {
                        Y = Y.divide(Z);
                    }
                }
                return Y;
            }
            default:
            {
                return y;
            }
            }
        }

        protected boolean getCompressionYTilde()
        {
            ECFieldElement X = getRawXCoord();
            if (X.isZero())
            {
                return false;
            }

            ECFieldElement Y = getRawYCoord();

            switch (getCurveCoordinateSystem())
            {
            case ECCurve.COORD_LAMBDA_AFFINE:
            case ECCurve.COORD_LAMBDA_PROJECTIVE:
            {
                // Y is actually Lambda (X + Y/X) here
                return Y.subtract(X).testBitZero();
            }
            default:
            {
                return Y.divide(X).testBitZero();
            }
            }
        }

        /**
         * Check, if two <code>ECPoint</code>s can be added or subtracted.
         * @param a The first <code>ECPoint</code> to check.
         * @param b The second <code>ECPoint</code> to check.
         * @throws IllegalArgumentException if <code>a</code> and <code>b</code>
         * cannot be added.
         */
        private static void checkPoints(ECPoint a, ECPoint b)
        {
            // Check, if points are on the same curve
            if (a.curve != b.curve)
            {
                throw new IllegalArgumentException("Only points on the same "
                        + "curve can be added or subtracted");
            }

//            ECFieldElement.F2m.checkFieldElements(a.x, b.x);
        }

        /* (non-Javadoc)
         * @see org.bouncycastle.math.ec.ECPoint#add(org.bouncycastle.math.ec.ECPoint)
         */
        public ECPoint add(ECPoint b)
        {
            checkPoints(this, b);
            return addSimple((ECPoint.F2m)b);
        }

        /**
         * Adds another <code>ECPoints.F2m</code> to <code>this</code> without
         * checking if both points are on the same curve. Used by multiplication
         * algorithms, because there all points are a multiple of the same point
         * and hence the checks can be omitted.
         * @param b The other <code>ECPoints.F2m</code> to add to
         * <code>this</code>.
         * @return <code>this + b</code>
         */
        public ECPoint.F2m addSimple(ECPoint.F2m b)
        {
            if (isInfinity())
            {
                return b;
            }
            if (b.isInfinity())
            {
                return this;
            }

            ECCurve curve = getCurve();
            int coord = curve.getCoordinateSystem();

            ECFieldElement X1 = this.x;
            ECFieldElement X2 = b.x;

            switch (coord)
            {
            case ECCurve.COORD_AFFINE:
            {
                ECFieldElement Y1 = this.y;
                ECFieldElement Y2 = b.y;

                if (X1.equals(X2))
                {
                    if (Y1.equals(Y2))
                    {
                        return (ECPoint.F2m)twice();
                    }

                    return (ECPoint.F2m)curve.getInfinity();
                }

                ECFieldElement sumX = X1.add(X2);
                ECFieldElement L = Y1.add(Y2).divide(sumX);

                ECFieldElement X3 = L.square().add(L).add(sumX).add(curve.getA());
                ECFieldElement Y3 = L.multiply(X1.add(X3)).add(X3).add(Y1);

                return new ECPoint.F2m(curve, X3, Y3, withCompression);
            }
            case ECCurve.COORD_HOMOGENEOUS:
            {
                ECFieldElement Y1 = this.y, Z1 = this.zs[0];
                ECFieldElement Y2 = b.y, Z2 = b.zs[0];

                boolean Z2IsOne = Z2.bitLength() == 1;

                ECFieldElement U1 = Z1.multiply(Y2); 
                ECFieldElement U2 = Z2IsOne ? Y1 : Y1.multiply(Z2);
                ECFieldElement U = U1.subtract(U2);
                ECFieldElement V1 = Z1.multiply(X2);
                ECFieldElement V2 = Z2IsOne ? X1 : X1.multiply(Z2);
                ECFieldElement V = V1.subtract(V2);

                if (V1.equals(V2))
                {
                    if (U1.equals(U2))
                    {
                        return (ECPoint.F2m)twice();
                    }

                    return (ECPoint.F2m)curve.getInfinity();
                }

                ECFieldElement VSq =  V.square();
                ECFieldElement W = Z2IsOne ? Z1 : Z1.multiply(Z2);
                ECFieldElement A = U.square().add(U.multiply(V).add(VSq.multiply(curve.getA()))).multiply(W).add(V.multiply(VSq));

                ECFieldElement X3 = V.multiply(A);
                ECFieldElement VSqZ2 = Z2IsOne ? VSq : VSq.multiply(Z2);
                ECFieldElement Y3 = VSqZ2.multiply(U.multiply(X1).add(Y1.multiply(V))).add(A.multiply(U.add(V)));
                ECFieldElement Z3 = VSq.multiply(V).multiply(W);

                return new ECPoint.F2m(curve, X3, Y3, new ECFieldElement[]{ Z3 }, withCompression);
            }
            case ECCurve.COORD_LAMBDA_PROJECTIVE:
            {
                if (X1.isZero())
                {
                    return b.addSimple(this);
                }

                ECFieldElement L1 = this.y, Z1 = this.zs[0];
                ECFieldElement L2 = b.y, Z2 = b.zs[0];

                boolean Z1IsOne = Z1.bitLength() == 1;
                ECFieldElement U2 = X2, S2 = L2;
                if (!Z1IsOne)
                {
                    U2 = U2.multiply(Z1);
                    S2 = S2.multiply(Z1);
                }

                boolean Z2IsOne = Z2.bitLength() == 1;
                ECFieldElement U1 = X1, S1 = L1;
                if (!Z2IsOne)
                {
                    U1 = U1.multiply(Z2);
                    S1 = S1.multiply(Z2);
                }

                ECFieldElement A = S1.add(S2);
                ECFieldElement B = U1.add(U2);

                if (B.isZero())
                {
                    if (A.isZero())
                    {
                        return (ECPoint.F2m)twice();
                    }

                    return (ECPoint.F2m)curve.getInfinity();
                }

                ECFieldElement X3, L3, Z3;
                if (X2.isZero())
                {
                    // TODO This can probably be optimized quite a bit

                    ECFieldElement Y1 = getYCoord(), Y2 = L2;
                    ECFieldElement L = Y1.add(Y2).divide(X1);

                    X3 = L.square().add(L).add(X1).add(curve.getA());
                    ECFieldElement Y3 = L.multiply(X1.add(X3)).add(X3).add(Y1);
                    L3 = X3.isZero() ? Y3 : Y3.divide(X3).add(X3);
                    Z3 = curve.fromBigInteger(ECConstants.ONE);
                }
                else
                {
                    B = B.square();
    
                    ECFieldElement AU1 = A.multiply(U1);
                    ECFieldElement AU2 = A.multiply(U2);
                    ECFieldElement ABZ2 = A.multiply(B);
                    if (!Z2IsOne)
                    {
                        ABZ2 = ABZ2.multiply(Z2);
                    }

                    X3 = AU1.multiply(AU2);
                    L3 = AU2.add(B).square().add(ABZ2.multiply(L1.add(Z1)));

                    Z3 = ABZ2;
                    if (!Z1IsOne)
                    {
                        Z3 = Z3.multiply(Z1);
                    }
                }

                return new ECPoint.F2m(curve, X3, L3, new ECFieldElement[]{ Z3 }, withCompression);
            }
            default:
            {
                throw new UnsupportedOperationException("unsupported coordinate system");
            }
            }
        }

        /* (non-Javadoc)
         * @see org.bouncycastle.math.ec.ECPoint#subtract(org.bouncycastle.math.ec.ECPoint)
         */
        public ECPoint subtract(ECPoint b)
        {
            checkPoints(this, b);
            return subtractSimple((ECPoint.F2m)b);
        }

        /**
         * Subtracts another <code>ECPoints.F2m</code> from <code>this</code>
         * without checking if both points are on the same curve. Used by
         * multiplication algorithms, because there all points are a multiple
         * of the same point and hence the checks can be omitted.
         * @param b The other <code>ECPoints.F2m</code> to subtract from
         * <code>this</code>.
         * @return <code>this - b</code>
         */
        public ECPoint.F2m subtractSimple(ECPoint.F2m b)
        {
            if (b.isInfinity())
            {
                return this;
            }

            // Add -b
            return addSimple((ECPoint.F2m)b.negate());
        }

        public ECPoint.F2m tau()
        {
            if (isInfinity())
            {
                return this;
            }

            ECCurve curve = getCurve();
            int coord = curve.getCoordinateSystem();

            ECFieldElement X1 = this.x;

            switch (coord)
            {
            case ECCurve.COORD_AFFINE:
            case ECCurve.COORD_LAMBDA_AFFINE:
            {
                ECFieldElement Y1 = this.y;
                return new ECPoint.F2m(curve, X1.square(), Y1.square(), withCompression);
            }
            case ECCurve.COORD_HOMOGENEOUS:
            case ECCurve.COORD_LAMBDA_PROJECTIVE:
            {
                ECFieldElement Y1 = this.y, Z1 = this.zs[0];
                return new ECPoint.F2m(curve, X1.square(), Y1.square(), new ECFieldElement[]{ Z1.square() }, withCompression);
            }
            default:
            {
                throw new UnsupportedOperationException("unsupported coordinate system");
            }
            }
        }

        public ECPoint twice()
        {
            if (isInfinity()) 
            {
                return this;
            }

            ECCurve curve = getCurve();

            ECFieldElement X1 = this.x;
            if (X1.isZero()) 
            {
                // A point with X == 0 is it's own additive inverse
                return curve.getInfinity();
            }

            int coord = curve.getCoordinateSystem();

            switch (coord)
            {
            case ECCurve.COORD_AFFINE:
            {
                ECFieldElement Y1 = this.y;

                ECFieldElement L1 = Y1.divide(X1).add(X1);

                ECFieldElement X3 = L1.square().add(L1).add(curve.getA());
                ECFieldElement Y3 = X1.square().add(X3.multiply(L1.addOne()));

                return new ECPoint.F2m(curve, X3, Y3, withCompression);
            }
            case ECCurve.COORD_HOMOGENEOUS:
            {
                ECFieldElement Y1 = this.y, Z1 = this.zs[0];

                boolean Z1IsOne = Z1.bitLength() == 1;
                ECFieldElement X1Z1 = Z1IsOne ? X1 : X1.multiply(Z1);
                ECFieldElement Y1Z1 = Z1IsOne ? Y1 : Y1.multiply(Z1);

                ECFieldElement X1Sq = X1.square();
                ECFieldElement S = X1Sq.add(Y1Z1);
                ECFieldElement V = X1Z1;
                ECFieldElement vSquared = V.square();
                ECFieldElement h = S.square().add(S.multiply(V)).add(curve.getA().multiply(vSquared));

                ECFieldElement X3 = V.multiply(h);
                ECFieldElement Y3 = h.multiply(S.add(V)).add(X1Sq.square().multiply(V));
                ECFieldElement Z3 = V.multiply(vSquared);    

                return new ECPoint.F2m(curve, X3, Y3, new ECFieldElement[]{ Z3 }, withCompression);
            }
            case ECCurve.COORD_LAMBDA_PROJECTIVE:
            {
                ECFieldElement L1 = this.y, Z1 = this.zs[0];

                boolean Z1IsOne = Z1.bitLength() == 1;
                ECFieldElement L1Z1 = Z1IsOne ? L1 : L1.multiply(Z1);
                ECFieldElement Z1Sq = Z1IsOne ? Z1 : Z1.square();
                ECFieldElement a = curve.getA();
                ECFieldElement aZ1Sq = Z1IsOne ? a : a.multiply(Z1Sq);
                ECFieldElement T = L1.square().add(L1Z1).add(aZ1Sq);

                ECFieldElement X3 = T.square();
                ECFieldElement Z3 = Z1IsOne ? T : T.multiply(Z1Sq);

                ECFieldElement b = curve.getB();
                ECFieldElement L3;
                if (b.bitLength() < (curve.getFieldSize() >> 1))
                {
                    ECFieldElement t1 = L1.add(X1).square();
                    ECFieldElement t2 = aZ1Sq.square();
                    ECFieldElement t3 = curve.getB().multiply(Z1Sq.square());
                    L3 = t1.add(T).add(Z1Sq).multiply(t1).add(t2.add(t3)).add(X3).add(a.addOne().multiply(Z3));
                }
                else
                {
                    ECFieldElement X1Z1 = Z1IsOne ? X1 : X1.multiply(Z1);
                    L3 = X1Z1.square().add(X3).add(T.multiply(L1Z1)).add(Z3);
                }

                return new ECPoint.F2m(curve, X3, L3, new ECFieldElement[]{ Z3 }, withCompression);
            }
            default:
            {
                throw new UnsupportedOperationException("unsupported coordinate system");
            }
            }
        }

        public ECPoint twicePlus(ECPoint b)
        {
            if (isInfinity()) 
            {
                return b;
            }
            if (b.isInfinity())
            {
                return twice();
            }

            ECCurve curve = getCurve();

            ECFieldElement X1 = this.x;
            if (X1.isZero()) 
            {
                // A point with X == 0 is it's own additive inverse
                return b;
            }

            int coord = curve.getCoordinateSystem();

            switch (coord)
            {
            case ECCurve.COORD_LAMBDA_PROJECTIVE:
            {
                // NOTE: twicePlus() only optimized for lambda-affine argument
                ECFieldElement X2 = b.x, Z2 = b.zs[0];
                if (X2.isZero() || Z2.bitLength() != 1)
                {
                    return twice().add(b);
                }

                ECFieldElement L1 = this.y, Z1 = this.zs[0];
                ECFieldElement L2 = b.y;

                ECFieldElement X1Sq = X1.square();
                ECFieldElement L1Sq = L1.square();
                ECFieldElement Z1Sq = Z1.square();
                ECFieldElement L1Z1 = L1.multiply(Z1);

                ECFieldElement T = curve.getA().multiply(Z1Sq).add(L1Sq).add(L1Z1);
                ECFieldElement L2plus1 = L2.addOne();
                ECFieldElement A = curve.getA().add(L2plus1).multiply(Z1Sq).add(L1Sq).multiply(T).add(X1Sq.multiply(Z1Sq));
                ECFieldElement X2Z1Sq = X2.multiply(Z1Sq);
                ECFieldElement B = X2Z1Sq.add(T).square();

                ECFieldElement X3 = A.square().multiply(X2Z1Sq);
                ECFieldElement Z3 = A.multiply(B).multiply(Z1Sq);
                ECFieldElement L3 = A.add(B).square().multiply(T).add(L2plus1.multiply(Z3));

                return new ECPoint.F2m(curve, X3, L3, new ECFieldElement[]{ Z3 }, withCompression);
            }
            default:
            {
                return twice().add(b);
            }
            }
        }

        protected void checkCurveEquation()
        {
            if (isInfinity())
            {
                return;
            }

            ECFieldElement Z;
            switch (getCurveCoordinateSystem())
            {
            case ECCurve.COORD_LAMBDA_AFFINE:
                Z = curve.fromBigInteger(BigInteger.ONE);
                break;
            case ECCurve.COORD_LAMBDA_PROJECTIVE:
                Z = this.zs[0];
                break;
            default:
                return;
            }

            if (Z.isZero())
            {
                throw new IllegalStateException();
            }

            ECFieldElement X = this.x;
            if (X.isZero())
            {
                // NOTE: For x == 0, we expect the affine-y instead of the lambda-y 
                ECFieldElement Y = this.y;
                if (!Y.square().equals(curve.getB().multiply(Z)))
                {
                    throw new IllegalStateException();
                }

                return;
            }

            ECFieldElement L = this.y;
            ECFieldElement XSq = X.square();
            ECFieldElement ZSq = Z.square();

            ECFieldElement lhs = L.square().add(L.multiply(Z)).add(getCurve().getA().multiply(ZSq)).multiply(XSq);
            ECFieldElement rhs = ZSq.square().multiply(getCurve().getB()).add(XSq.square());
            
            if (!lhs.equals(rhs))
            {
                throw new IllegalStateException("F2m Lambda-Projective invariant broken");
            }
        }

        public ECPoint negate()
        {
            if (this.isInfinity())
            {
                return this;
            }

            ECFieldElement X = this.x;
            if (X.isZero())
            {
                return this;
            }

            switch (getCurveCoordinateSystem())
            {
            case ECCurve.COORD_AFFINE:
            {
                ECFieldElement Y = this.y;
                return new ECPoint.F2m(curve, X, Y.add(X), withCompression);
            }
            case ECCurve.COORD_HOMOGENEOUS:
            {
                ECFieldElement Y = this.y, Z = this.zs[0];
                return new ECPoint.F2m(curve, X, Y.add(X), new ECFieldElement[]{ Z }, withCompression);
            }
            case ECCurve.COORD_LAMBDA_AFFINE:
            {
                ECFieldElement L = this.y;
                return new ECPoint.F2m(curve, X, L.addOne(), withCompression);
            }
            case ECCurve.COORD_LAMBDA_PROJECTIVE:
            {
                // L is actually Lambda (X + Y/X) here
                ECFieldElement L = this.y, Z = this.zs[0];
                return new ECPoint.F2m(curve, X, L.add(Z), new ECFieldElement[]{ Z }, withCompression);
            }
            default:
            {
                throw new UnsupportedOperationException("unsupported coordinate system");
            }
            }
        }
    }
}
