package Sample is

   type Object is private;

   function Create (Name : String) return Object;

   function Name (Item : Object) return String;

private

   Max_Name_Length : constant := 80;

   type Object is record
      Name   : String (1 .. Max_Name_Length) := (others => ' ');
      Length : Natural := 0;
   end record;

end Sample;
