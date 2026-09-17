with Ada.Text_IO; use Ada.Text_IO;

procedure Main is

   protected Counter is
      procedure Increment;
      function Value return Natural;
   private
      Count : Natural := 0;
   end Counter;

   protected body Counter is
      procedure Increment is
      begin
         Count := Count + 1;
      end Increment;

      function Value return Natural is
      begin
         return Count;
      end Value;
   end Counter;

   task type Worker (Id : Positive);

   task body Worker is
   begin
      for Step in 1 .. 5 loop
         Counter.Increment;
         Put_Line ("Worker" & Positive'Image (Id) & " step" & Integer'Image (Step));
         delay 0.05;
      end loop;
   end Worker;

   Workers : array (1 .. 3) of access Worker :=
     (new Worker (1), new Worker (2), new Worker (3));

begin
   delay 1.0;
   Put_Line ("Total steps:" & Natural'Image (Counter.Value));
end Main;
