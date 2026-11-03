
package com.yak.minisoc.service;

import com.yak.minisoc.entity.Role;
import com.yak.minisoc.entity.User;
import com.yak.minisoc.repository.RoleRepository;
import com.yak.minisoc.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(
        String username,
        String email,
        String password
    ) {
        username = username == null ? "" : username.trim();
        email = email == null ? "" :
            email.trim().toLowerCase(Locale.ROOT);

        if (!username.matches("[A-Za-z0-9_]{3,30}")) {
            throw new IllegalArgumentException(
                "Tên đăng nhập phải có 3-30 ký tự, " +
                    "chỉ gồm chữ, số và dấu gạch dưới."
            );
        }

        if (email.length() > 120 ||
            !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                "Email không hợp lệ."
            );
        }

        if (password == null ||
            password.length() < 12 ||
            password.getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            ).length > 72) {
            throw new IllegalArgumentException(
                "Mật khẩu phải có ít nhất 12 ký tự " +
                    "và không vượt quá 72 byte UTF-8."
            );
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException(
                "Tên đăng nhập đã tồn tại."
            );
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException(
                "Email đã được sử dụng."
            );
        }

        Role customerRole = roleRepository.findByCode("CUSTOMER")
            .orElseThrow(() -> new IllegalStateException(
                "Không tìm thấy quyền CUSTOMER."
            ));

        String hash = passwordEncoder.encode(password);

        User user = User.create(
            username,
            email,
            hash,
            null
        );

        // Người dùng đăng ký chỉ nhận quyền CUSTOMER.
        user.addRole(customerRole);

        userRepository.saveAndFlush(user);
    }
}
