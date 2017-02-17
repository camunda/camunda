resource "aws_security_group_rule" "from_out_to" {
  count                    = "${length(var.ports)}"
  type                     = "egress"
  from_port                = "${element(var.ports, count.index)}"
  to_port                  = "${element(var.ports, count.index)}"
  protocol                 = "${var.protocol}"
  security_group_id        = "${var.from}"
  source_security_group_id = "${var.to}"
}

resource "aws_security_group_rule" "to_in_from" {
  count                    = "${length(var.ports)}"
  type                     = "ingress"
  from_port                = "${element(var.ports, count.index)}"
  to_port                  = "${element(var.ports, count.index)}"
  protocol                 = "${var.protocol}"
  security_group_id        = "${var.to}"
  source_security_group_id = "${var.from}"
}
