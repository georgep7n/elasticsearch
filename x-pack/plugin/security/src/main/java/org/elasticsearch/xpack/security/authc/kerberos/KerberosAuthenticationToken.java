/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.security.authc.kerberos;

import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.xpack.core.security.authc.AuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/**
 * This class represents AuthenticationToken for Kerberos authentication using
 * SPNEGO mechanism. The token stores base 64 decoded token bytes, extracted
 * from the Authorization header with auth scheme 'Negotiate'.
 * <p>
 * Example Authorization header "Authorization: Negotiate
 * YIIChgYGKwYBBQUCoII..."
 * <p>
 * If there is any error handling during extraction of 'Negotiate' header then
 * it throws {@link ElasticsearchSecurityException} with
 * {@link RestStatus#UNAUTHORIZED} and header 'WWW-Authenticate: Negotiate'
 */
public final class KerberosAuthenticationToken implements AuthenticationToken {

    public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
    public static final String AUTH_HEADER = "Authorization";
    public static final String NEGOTIATE_AUTH_HEADER = "Negotiate ";

    private static final boolean IGNORE_CASE_AUTH_HEADER_MATCH = true;

    private final byte[] base64DecodedToken;

    public KerberosAuthenticationToken(final byte[] base64DecodedToken) {
        this.base64DecodedToken = base64DecodedToken;
    }

    /**
     * Extract token from authorization header and if it is valid
     * {@link #NEGOTIATE_AUTH_HEADER} then returns
     * {@link KerberosAuthenticationToken}
     *
     * @param authorizationHeader Authorization header from request
     * @return returns {@code null} if {@link #AUTH_HEADER} is empty or not an
     *         {@link #NEGOTIATE_AUTH_HEADER} else returns valid
     *         {@link KerberosAuthenticationToken}
     * @throws ElasticsearchSecurityException when negotiate header is invalid.
     */
    public static KerberosAuthenticationToken extractToken(final String authorizationHeader) {
        if (Strings.isNullOrEmpty(authorizationHeader)) {
            return null;
        }

        if (authorizationHeader.regionMatches(IGNORE_CASE_AUTH_HEADER_MATCH, 0, NEGOTIATE_AUTH_HEADER.trim(), 0,
                NEGOTIATE_AUTH_HEADER.trim().length()) == false) {
            return null;
        }

        final String base64EncodedToken = authorizationHeader.substring(NEGOTIATE_AUTH_HEADER.trim().length()).trim();
        if (Strings.isEmpty(base64EncodedToken)) {
            throw unauthorized("invalid negotiate authentication header value, expected base64 encoded token but value is empty", null);
        }
        final byte[] base64Token = base64EncodedToken.getBytes(StandardCharsets.UTF_8);
        byte[] decodedKerberosTicket = null;
        try {
            decodedKerberosTicket = Base64.getDecoder().decode(base64Token);
        } catch (IllegalArgumentException iae) {
            throw unauthorized("invalid negotiate authentication header value, could not decode base64 token {}", iae, base64EncodedToken);
        }

        return new KerberosAuthenticationToken(decodedKerberosTicket);
    }

    @Override
    public String principal() {
        return "<Unauthenticated Principal Name>";
    }

    @Override
    public Object credentials() {
        return base64DecodedToken;
    }

    @Override
    public void clearCredentials() {
        Arrays.fill(base64DecodedToken, (byte) 0);
    }

    @Override
    public int hashCode() {
        return Objects.hash(base64DecodedToken);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other)
            return true;
        if (other == null)
            return false;
        if (getClass() != other.getClass())
            return false;
        final KerberosAuthenticationToken otherKerbToken = (KerberosAuthenticationToken) other;
        return Objects.equals(otherKerbToken.credentials(), credentials());
    }

    private static ElasticsearchSecurityException unauthorized(final String message, final Throwable cause, final Object... args) {
        ElasticsearchSecurityException ese = new ElasticsearchSecurityException(message, RestStatus.UNAUTHORIZED, cause, args);
        ese.addHeader(WWW_AUTHENTICATE, NEGOTIATE_AUTH_HEADER.trim());
        return ese;
    }
}
