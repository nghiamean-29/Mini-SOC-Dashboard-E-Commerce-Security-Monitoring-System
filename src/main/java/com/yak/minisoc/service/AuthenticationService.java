
package com.yak.minisoc.service;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import com.yak.minisoc.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // The Swing login opens the SOC dashboard directly.
    public User authenticate(String username, char[] password) {

        if (username == null || username.isBlank()
            || password == null || password.length == 0) {
            throw new IllegalArgumentException(
                "Tên đăng nhập hoặc mật khẩu không hợp lệ"
            );
        }

        User user = userRepository
            .findWithRolesByUsername(username.trim())
            .orElseThrow(() -> new IllegalArgumentException(
                "Tên đăng nhập hoặc mật khẩu không đúng"
            ));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException(
                "Tài khoản không được phép đăng nhập"
            );
        }

        String rawPassword = new String(password);

        if (!passwordEncoder.matches(
            rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(
                "Tên đăng nhập hoặc mật khẩu không đúng"
            );
        }

        if (!user.hasRole("ADMIN") && !user.hasRole("SOC_ANALYST")) {
            throw new IllegalArgumentException(
                "Tài khoản không được phép truy cập bảng điều khiển SOC"
            );
        }

        return user;
    }
}
