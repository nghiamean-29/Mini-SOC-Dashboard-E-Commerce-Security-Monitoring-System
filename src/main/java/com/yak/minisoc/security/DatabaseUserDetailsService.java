
package com.yak.minisoc.security;

import com.yak.minisoc.entity.User;
import com.yak.minisoc.entity.UserStatus;
import com.yak.minisoc.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username)
        throws UsernameNotFoundException {

        User user = userRepository.findWithRolesByUsername(username)
            .orElseThrow(() ->
                new UsernameNotFoundException("Invalid username or password"));

        UserStatus status = user.getStatus();

        var authorities = user.getRoleCodes().stream()
            .map(code -> new SimpleGrantedAuthority(
                "ROLE_" + code.toUpperCase(Locale.ROOT)))
            .toList();

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getUsername())
            .password(user.getPasswordHash())
            .authorities(authorities)
            .accountLocked(status == UserStatus.LOCKED)
            .disabled(status == UserStatus.DISABLED)
            .build();
    }
}
