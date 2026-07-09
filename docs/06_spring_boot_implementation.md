# Spring Boot 実装方針・コード例

## 1. プロジェクト構成

### pom.xml（主要依存関係）

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-mail</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## 2. エンティティ

### User.java

```java
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.STUDENT;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "icon_path")
    private String iconPath;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum Role { STUDENT, DEVELOPER }
}
```

### EmailVerification.java

```java
@Entity
@Table(name = "email_verifications")
@Getter @Setter @NoArgsConstructor
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
```

---

## 3. サービス層

### AuthService.java

```java
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final JwtUtil jwtUtil;

    @Value("${app.mail.from-address}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.mail.verify-url-base}")
    private String verifyUrlBase;

    @Value("${app.mail.token-expire-hours}")
    private int tokenExpireHours;

    public void signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new EmailAlreadyExistsException(req.getEmail());
        }

        User user = new User();
        user.setLastName(req.getLastName());
        user.setFirstName(req.getFirstName());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getTempPassword()));
        userRepository.save(user);

        String token = UUID.randomUUID().toString();
        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setToken(token);
        verification.setExpiresAt(LocalDateTime.now().plusHours(tokenExpireHours));
        verificationRepository.save(verification);

        sendVerificationEmail(user, token);
    }

    public LoginResponse verify(String token) {
        EmailVerification verification = verificationRepository.findByToken(token)
            .orElseThrow(TokenInvalidException::new);

        if (verification.isExpired()) throw new TokenExpiredException();
        if (verification.isUsed())   throw new TokenAlreadyUsedException();

        verification.setUsedAt(LocalDateTime.now());
        User user = verification.getUser();
        user.setVerified(true);

        return new LoginResponse(jwtUtil.generateAccessToken(user), user);
    }

    private void sendVerificationEmail(User user, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(String.format("%s <%s>", fromName, fromAddress));
        message.setTo(user.getEmail());
        message.setSubject("【FE_STS】メールアドレスの確認");
        message.setText(String.format("""
            %s %s さん

            FE_STS にご登録いただきありがとうございます。
            以下のリンクをクリックしてメールアドレスを確認してください。

            %s?token=%s

            このリンクは発行から %d 時間有効です。
            心当たりのない場合はこのメールを無視してください。

            FE_STS 運営チーム
            """,
            user.getLastName(), user.getFirstName(),
            verifyUrlBase, token, tokenExpireHours));
        mailSender.send(message);
    }
}
```

---

## 4. コントローラー

### AuthController.java

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> signup(@Valid @RequestBody SignupRequest req) {
        authService.signup(req);
        return ResponseEntity.accepted()
            .body(new MessageResponse("確認メールを送信しました。24時間以内にリンクをクリックしてください。"));
    }

    @GetMapping("/verify")
    public ResponseEntity<LoginResponse> verify(@RequestParam String token) {
        return ResponseEntity.ok(authService.verify(token));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
```

---

## 5. JWT ユーティリティ

```java
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expire-ms:3600000}")
    private long accessTokenExpireMs;

    public String generateAccessToken(User user) {
        return Jwts.builder()
            .subject(user.getEmail())
            .claim("userId", user.getId())
            .claim("role", user.getRole().name())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpireMs))
            .signWith(getSigningKey())
            .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
```

---

## 6. Spring Security 設定

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

---

## 7. CORS 設定

```java
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://your-domain.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

---

## 8. 開発者専用インスタントログイン

```java
@RestController
@RequestMapping("/api/auth")
@Profile("!prod")
@RequiredArgsConstructor
public class DevAuthController {

    private final JwtUtil jwtUtil;

    @PostMapping("/dev-login")
    public ResponseEntity<LoginResponse> devLogin() {
        User devUser = new User();
        devUser.setId(-1L);
        devUser.setLastName("-");
        devUser.setFirstName("-");
        devUser.setEmail("dev@localhost");
        devUser.setRole(User.Role.DEVELOPER);
        devUser.setVerified(true);

        return ResponseEntity.ok(new LoginResponse(jwtUtil.generateAccessToken(devUser), devUser));
    }
}
```

フロントエンドで `role === 'DEVELOPER'` を判定し、アイコンをデベロッパーマークに切り替える。

---

## 9. application.yml（全体）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/fests?useSSL=true&serverTimezone=Asia/Tokyo
    username: fests_user
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    baseline-on-migrate: true
  mail:
    host: smtp-relay.brevo.com
    port: 587
    username: ${BREVO_SMTP_USER}
    password: ${BREVO_SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true

app:
  jwt:
    secret: ${JWT_SECRET}
    access-token-expire-ms: 3600000     # 1時間
    refresh-token-expire-ms: 604800000  # 7日
  mail:
    from-name: "FE_STS 運営チーム"
    from-address: "noreply@your-domain.com"
    verify-url-base: "https://your-domain.com/verify"
    token-expire-hours: 24

server:
  port: 8080
```
