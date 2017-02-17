resource "aws_security_group_rule" "default" {
  count             = "${length(var.ports)}"
  type              = "${var.type}"
  from_port         = "${element(var.ports, count.index)}"
  to_port           = "${element(var.ports, count.index)}"
  protocol          = "${var.protocol}"
  security_group_id = "${var.group}"
  cidr_blocks       = "${var.cidr_blocks}"
}
