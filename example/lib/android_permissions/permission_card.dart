import 'package:flutter/material.dart';

class PermissionCard extends StatelessWidget {
  const PermissionCard({
    required this.name,
    required this.value,
    this.onTap,
    super.key,
  });

  final String name;
  final bool value;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: value ? 0.25 : 1,
      child: Container(
        padding: const EdgeInsets.fromLTRB(24, 12, 8, 12),
        decoration: ShapeDecoration(
          color: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(10),
          ),
          shadows: const [
            BoxShadow(
              color: Color(0x33333333),
              blurRadius: 1,
            ),
          ],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              name,
              style: const TextStyle(
                fontWeight: FontWeight.bold,
              ),
            ),
            if (value) ...[
              const SizedBox(height: 45),
            ] else ...[
              IntrinsicWidth(
                child: ElevatedButton(
                  onPressed: onTap,
                  child: const Text(
                    'Allow',
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
