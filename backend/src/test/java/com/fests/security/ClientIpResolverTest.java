package com.fests.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #25: X-Forwarded-For の先頭要素はクライアントが詐称できるため、
 * レート制限のキーには使えない。信頼するプロキシ段数だけ右から遡った値を使う。
 */
class ClientIpResolverTest {

    private static final String REMOTE = "10.0.0.1";

    @Test
    void spoofedLeadingEntryIsIgnored() {
        // 攻撃者が "1.2.3.4" を自称し、その後ろに信頼できるプロキシが実IPを追記した状態
        String ip = ClientIpResolver.resolve("1.2.3.4, 203.0.113.9", REMOTE, 1);
        assertThat(ip).isEqualTo("203.0.113.9");
    }

    @Test
    void everyRequestFromSameClientResolvesToSameIpEvenIfHeaderChanges() {
        String first = ClientIpResolver.resolve("1.1.1.1, 203.0.113.9", REMOTE, 1);
        String second = ClientIpResolver.resolve("2.2.2.2, 203.0.113.9", REMOTE, 1);
        // 詐称部分が変わってもキーが変わらない = レート制限を回避できない
        assertThat(first).isEqualTo(second);
    }

    @Test
    void twoTrustedProxiesGoTwoHopsBack() {
        String ip = ClientIpResolver.resolve("1.2.3.4, 203.0.113.9, 198.51.100.7", REMOTE, 2);
        assertThat(ip).isEqualTo("203.0.113.9");
    }

    @Test
    void headerShorterThanExpectedFallsBackToRemoteAddr() {
        assertThat(ClientIpResolver.resolve("1.2.3.4", REMOTE, 2)).isEqualTo(REMOTE);
    }

    @Test
    void missingOrBlankHeaderFallsBackToRemoteAddr() {
        assertThat(ClientIpResolver.resolve(null, REMOTE, 1)).isEqualTo(REMOTE);
        assertThat(ClientIpResolver.resolve("   ", REMOTE, 1)).isEqualTo(REMOTE);
    }

    @Test
    void zeroTrustedProxiesIgnoresHeaderEntirely() {
        assertThat(ClientIpResolver.resolve("1.2.3.4, 203.0.113.9", REMOTE, 0)).isEqualTo(REMOTE);
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertThat(ClientIpResolver.resolve("1.2.3.4 ,  203.0.113.9  ", REMOTE, 1))
            .isEqualTo("203.0.113.9");
    }
}
