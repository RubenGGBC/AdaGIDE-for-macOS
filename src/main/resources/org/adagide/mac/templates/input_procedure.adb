with Ada.Text_IO;          use Ada.Text_IO;
with Ada.Integer_Text_IO;  use Ada.Integer_Text_IO;

procedure Main is
   Value : Integer;
begin
   Put ("Enter an integer: ");
   Get (Value);
   Put ("You entered ");
   Put (Value, Width => 0);
   New_Line;
end Main;
