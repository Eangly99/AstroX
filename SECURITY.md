# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 2.x.x   | ✅ Current          |
| 1.x.x   | ⚠️ Security fixes only |

## Reporting a Vulnerability

If you discover a security vulnerability in AstroX, please report it responsibly:

1.  open a public GitHub issue
2.  Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact assessment
   - Suggested fix (if any)

## Response Timeline

- **Acknowledgment**: Within 48 hours
- **Assessment**: Within 7 days
- **Fix Release**: Within 30 days for critical issues

## Responsible Use

AstroX is designed exclusively for **authorized security research and penetration testing**.
Use of this tool against systems without explicit authorization is illegal and unethical.

## Audit Log Verification

AstroX maintains a tamper-evident audit log at `~/.astrox/audit.log`.
Verify integrity with:

```bash
java -jar AstroX.jar verify-audit --key <your-master-key>
```
